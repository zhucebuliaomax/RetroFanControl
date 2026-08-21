# RetroControl Runtime Architecture

## Scope

This document describes the current runtime behavior of the Android application. The four user-facing control modules are documented separately:

- Fan Curve
- Joystick
- Button Layout
- Frequency Profile

Charging control, USB thermal fan control, telemetry, app-profile switching, Quick Settings tiles, and the overlay are supporting systems rather than members of the four-control model.

## Service lifecycle

`SystemControlService` is the long-lived owner of hardware state. It is an Android foreground service and returns `START_STICKY`. It is started by the main activity, Quick Settings actions, setting changes, and optionally `BootReceiver` after boot. The default configuration enables automatic start.

On creation the service:

1. Loads the active fan, USB thermal, joystick, button-layout, frequency, charging, and overlay settings.
2. Starts the foreground notification.
3. Starts the foreground-app monitor.
4. Starts the combined fan and telemetry loop.
5. Suppresses kernel thermal trips that are bound to `pwm-fan`.
6. Applies the resolved one-shot hardware controls.
7. Registers screen and battery receivers.

The service remains alive even if some or all control modules require no periodic work. This is intentional for system-tool behavior, but the current implementation does not lower its background activity enough when work becomes idle.

## Profile resolution

The service detects the foreground package and resolves a preset plus any per-app overrides. A profile can select one item from each of the four control modules. A direct Quick Settings selection can also take part in resolution.

The current foreground-app detector runs once per second and executes `dumpsys activity activities` through the persistent root shell. If no resumed activity is parsed, it falls back to `dumpsys window windows`.

There is no equivalent sysfs node. Sysfs represents kernel devices and drivers; the resumed Android activity is ActivityManager/WindowManager framework state. The current ROM's `cmd activity help` exposes no lightweight command that directly returns the resumed package. Possible replacements must therefore be Android framework/event APIs, a smaller dumpsys query if the ROM supports one, or a less frequent/event-gated poll—not a hardware sysfs read.

When the detected package changes, the service resolves and potentially reapplies all four controls, updates Quick Settings tiles, and optionally displays a profile-switch toast.

## Current periodic work

| Work | Current cadence | Screen-off behavior | Root/sysfs cost |
|---|---:|---|---|
| Main fan/control loop | 300 ms while active | Cancelled after 5 s if unplugged; charging USB remains active | Changed output only |
| Application thermal read | 500 ms overlay / 1 s normal / 3 s cool recheck | Stops after 5 s | Opens selected cached sensor `temp` files |
| USB thermal read | 2 s interactive fallback | 1 s while charging; stopped if unplugged | Opens selected USB sensor `temp` file |
| Current CPU-frequency telemetry | 500 ms | Stops after 5 s | One root-shell batch reading every CPU `scaling_cur_freq` |
| Foreground-package detection | 1 s | Stops after 5 s | One full activity dumpsys; sometimes a second window dumpsys |
| Foreground notification update | Event driven | Event driven | NotificationManager update on resolved-state changes |
| Joystick RGB effects | Mode dependent | Stopped immediately | See the joystick document |
| Charging threshold | Event driven | Continues on battery events | Conditional sysfs read/write/verification |

The process does not explicitly acquire a wake lock. Android deep suspend can therefore defer coroutine timers. Nevertheless, while the device is awake, charging, or being woken by another component, these loops create avoidable CPU, Binder, root-shell, and sysfs activity.

## Screen state

On `SCREEN_OFF`, joystick RGB animation and the telemetry overlay stop
immediately; the overlay preference is cleared and its Quick Settings tile is
refreshed. After five seconds the application fan and foreground-app monitor are
suspended. Without external power, the fan loop and all periodic sampling stop
after a verified PWM and `cur_state` zero. During charging, USB thermal control
continues at one sample per second while presentation sampling remains stopped.

On `USER_PRESENT`, application fan control and joystick behavior resume. The current implementation waits for unlock rather than merely screen-on.

## Telemetry and notification

`TelemetryRepository` is a process-local `StateFlow` shared by the dashboard, overlay, notification, and control service. Thermal and CPU-frequency telemetry are currently produced inside the fan loop, so they are sampled even when no visible consumer needs them.

The foreground notification currently shows the active fan curve, fan percentage, average CPU temperature, and average GPU temperature. It is rebuilt and sent every two seconds. This dynamic display is not required for service correctness.

The desired notification model is a low-churn status summary of the four resolved controls: Fan Curve, Joystick, Button Layout, and Frequency Profile. It should update only when one of those resolved selections changes, not on temperature or fan-output changes.

## Overlay

The overlay consumes thermal, fan, and CPU-frequency telemetry. It provides fan and frequency quick adjustment. The current service reads current CPU frequencies whether or not the overlay is enabled. CPU-frequency sampling is only operationally necessary while the overlay is visible; dashboard presentation can use a slower or lifecycle-bound stream if live values remain desirable there.

## Charging and USB thermal roles

Charging threshold control is event driven. It reads sticky `ACTION_BATTERY_CHANGED` state and writes `/sys/class/qcom-battery/charging_enabled` only when necessary, then verifies the value.

USB thermal fan control shares the fan coroutine but reads only the selected USB
sensor. It is eligible only while external power is connected. Interactive USB
fallback uses a two-second cadence; screen-off charging uses one second. An
unplugged, settled screen-off state cancels the coroutine completely, while a
power-connect event restarts it with an immediate USB sample.

## RP6 device observations

Read-only ADB verification on 2026-08-20 found:

- Device: Retroid Pocket 6.
- Fan cooling device: `/sys/class/thermal/cooling_device34`.
- Cooling states: `0..8`.
- USB sensors: `thermal_zone53` (`usb-therm`) and `thermal_zone57` (`usb`).
- The application was running as an active foreground, started service.
- All observed CPU/GPU trips bound to `pwm-fan` were set to `125000` millidegrees Celsius.
- The observed `usb-therm` zone was disabled and its fan trips were also set to `125000`.
- The device was not connected to external power during the observation.

These observations confirm current behavior on this device, not a portable contract for every ROM or kernel.
