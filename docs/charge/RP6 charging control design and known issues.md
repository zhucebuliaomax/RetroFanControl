# RP6 Charging Control: Design, Validation, and Known Issues

## Status

This document describes the charging-control implementation currently present in RetroControl for the Retroid Pocket 6 running LineageOS. It also records the behavior observed on a real RP6 and the unresolved transition problem between bypass charging and battery charging.

The current practical policy is:

- Rapid/slow charge selection is usable and has been verified on hardware.
- Entering bypass charge works.
- Returning from bypass charge is not reliable without disconnecting and reconnecting the USB cable.
- RetroControl does not currently attempt a USB/UCSI reset or another automatic recovery.
- If charging does not resume after leaving bypass mode, physically reconnect the cable.

The candidate kernel-interface fix described later is an investigation note, not implemented or fully validated behavior.

## Hardware and software context

- Device: Retroid Pocket 6 (RP6), Qualcomm SM8550.
- ROM: LineageOS using the Qualcomm `qti_battery_charger` driver.
- Device tree: [LineageOS/android_device_retroidpocket_RP6](https://github.com/LineageOS/android_device_retroidpocket_RP6).
- RetroControl requires root for all charging-related sysfs writes.
- The LineageOS `IFastCharge` service reports no supported modes on this device, so the standard Lineage fast-charge HAL cannot provide this feature.

USB Power Delivery controls the negotiated input voltage. RetroControl does not force a USB voltage. Charge speed is controlled by limiting the battery fast-charge current (FCC); the charger and PD firmware then select an appropriate voltage and input current.

## Relevant kernel interfaces

The following interfaces were found and tested on the RP6:

| Interface | Purpose | RetroControl usage |
| --- | --- | --- |
| `/sys/class/power_supply/battery/charge_control_limit` | Selects a thermal/FCC level | Rapid/slow charge control |
| `/sys/class/power_supply/battery/charge_control_limit_max` | Highest valid level index | Validates the requested level; observed value: `13` |
| `/sys/class/qcom-battery/charging_enabled` | Enables or disables battery charging through the Qualcomm driver | Current bypass implementation |
| `/sys/class/qcom-battery/restrict_chg` | Enables or clears the driver's FCC restriction | Not used by the current implementation; candidate bypass-exit interface |
| `/sys/class/qcom-battery/restrict_cur` | Stores the restricted FCC in microamps | Not used by the current implementation; candidate bypass-exit interface |

The running RP6 device tree exposes a `qcom,thermal-mitigation-step` of 500,000 microamps. The observed firmware FCC maximum is 7,200,000 microamps. With the step-based implementation in `qti_battery_charger`, the approximate requested FCC is:

```text
FCC(level) = 7,200,000 uA - level * 500,000 uA
```

The application uses:

| Mode | `charge_control_limit` | Approximate FCC |
| --- | ---: | ---: |
| Rapid | `0` | 7.2 A maximum request |
| Slow | `10` | 2.2 A maximum request |

These values are upper limits, not guaranteed battery currents. Firmware, battery state, temperature, cable capability, charger capability, and power used by the running system can all reduce the measured current.

## Current RetroControl design

### Bypass and threshold control

The bypass-charging Tile has three selected modes:

| Selected mode | Requested battery-charging state |
| --- | --- |
| Normal | Enabled |
| Bypass | Disabled |
| Threshold | Enabled below the selected threshold; disabled after the threshold is reached |

The selected mode is stored independently from the charge-speed preference. `ChargingControlLogic` converts the mode, battery percentage, and threshold latch into a requested Boolean charging state. `ChargingControlCoordinator` applies that decision through `ChargingController`.

The current low-level implementation writes:

```text
/sys/class/qcom-battery/charging_enabled = 0 or 1
```

Disabling charging with `0` has worked reliably during testing. The current code writes `1` more than once on a disabled-to-enabled transition as an attempted recovery workaround. Real-device testing showed that repeated writes do not reliably restore the actual charging current, even when the sysfs file reads back as `1`.

The bypass Tile currently presents the selected policy. For example, its subtitle can say `Normal charging` even if the hardware is stuck in a non-charging state after leaving bypass. It should therefore not be treated as proof that battery current is flowing.

### Rapid and slow charge control

`ChargeSpeedController` writes only `charge_control_limit`:

```text
Rapid: 0
Slow:  10
```

It first reads `charge_control_limit_max`, rejects an unsupported level, writes the requested level, and verifies the readback. This uses the kernel's normal FCC/thermal-limit path and does not disable firmware thermal protection.

The slow/rapid preference is persistent:

```text
slow_charging_enabled = false  # rapid, default
slow_charging_enabled = true   # slow
```

It is included in RetroControl configuration export/import. The saved slow mode is restored on boot and when external power is connected.

### Charging-speed Quick Settings Tile

The charging-speed Tile deliberately separates a saved speed preference from the current physical charging state.

The Tile is considered actively charging only when all of the following are true:

1. Android reports external power connected.
2. `ACTION_BATTERY_CHANGED` reports `BATTERY_STATUS_CHARGING`.
3. The selected bypass/threshold policy currently requests charging to be enabled.

Its presentation is:

| Condition | Tile state | Subtitle | Icon |
| --- | --- | --- | --- |
| Not actively charging | Inactive | `Not charging` | `icon/charge/charge_off` |
| Actively charging, rapid selected | Active | `Rapid charge` | `icon/charge/charge_rapid` |
| Actively charging, slow selected | Active | `Slow charge · ≈10 W` | `icon/charge/charge_slow` |

Tapping the Tile while actively charging toggles rapid and slow mode. Tapping it while not charging does not change the saved preference and shows a `Not charging` message.

The Tile refreshes on battery broadcasts, cable connection changes, bypass/threshold changes, service startup, boot handling, and successful speed changes. A weak reference to the currently listening Tile instance is used to update an already open Quick Settings panel immediately; `requestListeningState()` alone did not consistently refresh a second visible Tile.

### Interaction between the two controls

The charge-speed preference remains saved while bypass or threshold logic stops battery charging. This is intentional: speed describes what should be applied the next time battery charging is active.

| External power | Bypass decision | Android battery status | Speed Tile |
| --- | --- | --- | --- |
| Disconnected | Any | Not charging/discharging | Off, `Not charging` |
| Connected | Charging disabled | Not charging | Off, `Not charging` |
| Connected | Charging enabled | Charging | On, rapid or slow |
| Connected | Charging enabled | Not charging | Off, `Not charging` |

The final row is important. It covers full-battery behavior, firmware protection, and the known stuck state after leaving bypass. Merely reading `charging_enabled=1` is insufficient because that value describes the driver's internal flags, not confirmed battery current.

## Real-device validation

The following results were observed on the connected RP6. Measurements are snapshots rather than laboratory power measurements.

### Fresh cable connection in slow mode

- Selected bypass mode: Normal.
- Saved speed: Slow.
- Battery level: approximately 88%.
- `charge_control_limit`: `10`.
- Android status: Charging.
- Battery current: approximately 2.08 A.
- USB input: approximately 8.88 V and 1.20-1.34 A, roughly 10 W.

This confirms that normal charging plus the level-10 slow-charge limit works after a fresh cable connection.

### Switching to rapid mode while charging

- The speed Tile changed the preference to Rapid.
- `charge_control_limit` changed from `10` to `0`.
- Android remained in Charging state.
- Battery current rose to approximately 3.08 A in that test snapshot.
- USB input was approximately 8.78 V and 1.89 A.

This confirms that rapid/slow switching works without renegotiating the cable when battery charging is already active.

### Entering bypass mode

- `charging_enabled` changed to `0`.
- `restrict_chg` became `1`.
- Android changed to Not charging.
- Reported battery charge current fell to zero.
- The charging-speed Tile correctly became inactive and displayed `Not charging` after the cross-Tile refresh fix.

### Leaving bypass mode

- The selected policy changed away from Bypass and requested charging.
- `charging_enabled` read back as `1`.
- `restrict_chg` read back as `0`.
- `charge_control_limit` remained valid.
- Android nevertheless remained Not charging and battery charge current remained zero.
- Repeated `charging_enabled=1` writes, delayed writes, speed-level toggles, and a USB input-current reset did not reliably recover charging.
- Physically disconnecting and reconnecting the USB cable restored charging.

This is the unresolved issue. The charging-speed Tile is behaving correctly when it displays `Not charging` in this state. The misleading part is the bypass Tile's selected-mode subtitle, not the battery-status test used by the speed Tile.

## Probable kernel cause

The Qualcomm driver applies a restriction clamp inside `__battery_psy_set_charge_current()`:

```c
if (bcdev->restrict_chg_en) {
    fcc_ua = min(fcc_ua, bcdev->restrict_fcc_ua);
    fcc_ua = min(fcc_ua, bcdev->thermal_fcc_ua);
}
```

After bypass is enabled, the relevant internal state is effectively:

```text
restrict_chg_en = true
restrict_fcc_ua = 0
```

In the driver's `charging_enabled_store(true)` path, the call order is:

1. Call `__battery_psy_set_charge_current(thermal_fcc_ua)`.
2. Set `restrict_fcc_ua = thermal_fcc_ua`.
3. Clear `restrict_chg_en`.

At step 1, restriction is still enabled and its stored current is still zero. The requested thermal FCC can therefore be clamped to zero before the internal restriction fields are repaired. Sysfs subsequently reports charging as enabled because the fields have changed, even though the firmware may still have received a zero-current request.

This ordering explains all observed symptoms, but it should still be treated as a source-based diagnosis rather than a fully verified kernel fix.

## Candidate future fix

The simpler candidate is to stop using `charging_enabled=1` when leaving bypass and use the driver's restriction attributes in an order that avoids the zero clamp:

1. Apply the desired `charge_control_limit` (`0` for rapid or `10` for slow).
2. Set `restrict_cur` to an FCC no greater than the active thermal FCC.
3. Write `0` to `restrict_chg`.
4. Verify actual Android charging status and battery current, not only sysfs readback.

The important difference is that `restrict_chg_store(false)` clears `restrict_chg_en` before calling `__battery_psy_set_charge_current(thermal_fcc_ua)`. That order should avoid the stale zero-current clamp.

A future implementation should coordinate bypass and speed writes in one serialized hardware operation. It should not let `ChargingController` and `ChargeSpeedController` race independently during a mode transition. It also needs to account for thermal throttling: `restrict_cur` rejects a value above the current `thermal_fcc_ua`.

Suggested validation sequence for this candidate:

1. Begin with a fresh physical cable connection and confirm Android reports Charging.
2. Test both rapid and slow limits before bypass.
3. Enter bypass and confirm Android reports Not charging and current falls to zero.
4. Leave bypass using the ordered `restrict_cur`/`restrict_chg` path.
5. Sample status, battery current, USB voltage/current, and all five relevant sysfs attributes for several seconds.
6. Repeat for both rapid and slow selections, threshold-triggered bypass, full battery, warm-device thermal limiting, boot restore, and cable reconnect.

No UCSI unbind/rebind or forced Type-C role change is planned. Those approaches are disproportionate for a Quick Settings control, may disturb the USB data connection, and were not needed to establish the likely driver-ordering issue.

## Diagnostic commands

Run these from a root ADB shell. Paths and available properties may change with a future kernel.

```shell
cat /sys/class/qcom-battery/charging_enabled
cat /sys/class/qcom-battery/restrict_chg
cat /sys/class/qcom-battery/restrict_cur
cat /sys/class/power_supply/battery/charge_control_limit
cat /sys/class/power_supply/battery/charge_control_limit_max
cat /sys/class/power_supply/battery/status
cat /sys/class/power_supply/battery/current_now
cat /sys/class/power_supply/usb/online
cat /sys/class/power_supply/usb/voltage_now
cat /sys/class/power_supply/usb/current_now
dumpsys battery
```

For measurements, remember that current sign conventions can differ between power-supply drivers. Use the Android charging status and changes across several samples rather than treating one `current_now` value as definitive.

## Source map

| Area | Current implementation |
| --- | --- |
| Bypass/threshold state and decision | `app/src/main/java/com/mmax/retrocontrol/data/ChargingControlData.kt` |
| Bypass sysfs writes and Android battery-state reading | `app/src/main/java/com/mmax/retrocontrol/hardware/ChargingController.kt` |
| Serialized bypass decision application | `app/src/main/java/com/mmax/retrocontrol/service/ChargingControlCoordinator.kt` |
| Rapid/slow preference | `app/src/main/java/com/mmax/retrocontrol/data/ChargeSpeedData.kt` |
| Rapid/slow sysfs writes | `app/src/main/java/com/mmax/retrocontrol/hardware/ChargeSpeedController.kt` |
| Bypass Quick Settings Tile | `app/src/main/java/com/mmax/retrocontrol/tile/ChargingQuickSettingsTile.kt` |
| Charging-speed Quick Settings Tile | `app/src/main/java/com/mmax/retrocontrol/tile/ChargeSpeedQuickSettingsTile.kt` |
| Cable-boundary restoration | `app/src/main/java/com/mmax/retrocontrol/service/ChargingPowerReceiver.kt` |
| Runtime coordination and Tile refreshes | `app/src/main/java/com/mmax/retrocontrol/service/SystemControlService.kt` |
| Boot restoration | `app/src/main/java/com/mmax/retrocontrol/service/BootReceiver.kt` |

## Safety constraints for future work

- Keep Qualcomm firmware and kernel thermal protection active.
- Treat FCC values as limits; do not attempt to force a fixed USB-PD voltage.
- Serialize speed and bypass writes during transitions.
- Verify the physical charging state, not only the requested mode or sysfs readback.
- Fall back safely to an inactive `Not charging` Tile when status is ambiguous.
- Do not add automatic USB-controller resets unless their data-path and power-path side effects are understood and explicitly accepted.

