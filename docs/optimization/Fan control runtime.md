# Fan Curve Control Runtime

## Ownership model

RetroControl combines two independent curve inputs and writes the higher requested output:

1. The active application fan curve, driven by CPU/GPU temperature.
2. The USB thermal curve, driven by USB temperature.

`KernelFanThermalController` discovers CPU, GPU, and USB thermal trips whose cooling device type is `pwm-fan`. It stores original trip temperatures, temporarily disables each affected zone, raises the fan-bound trips to 125°C, and restores the intended zone mode. CPU/GPU zones normally remain enabled so unrelated throttling and protection continue to work; USB zones are disabled because RetroControl owns their fan policy. A user option can disable broader CPU/GPU thermal protection, but that is separate from fan ownership.

On the RP6 verified on 2026-08-20, the relevant fan trips were at 125°C and the USB fan zone was disabled. The kernel therefore should not overwrite the application-owned fan state during normal operation. Rewriting an unchanged PWM every 300 ms is not needed as the primary ownership mechanism on this configuration.

## Temperature input

Thermal-zone discovery is dynamic and cached. Zone `type` values are classified as CPU, GPU, DDR, battery, USB, or ignored. Each sample still opens every selected zone's `temp` file.

The application curve uses the hotter of:

- Average temperature of all discovered CPU readings.
- Average temperature of all discovered GPU readings.

It does not use the hottest individual sensor. The USB curve uses the first classified USB reading. On the verified device this ordering makes `usb-therm` the likely USB control input, while a second `usb` zone also exists.

Current temperature sampling interval: **500 ms**, in both screen-on and screen-off states and whether or not charging is connected.

## Curve and response algorithm

Fan curves use linear interpolation between temperature/output points. Temperatures below the first point produce 0%. The final point's output is held above the final temperature.

The application and USB paths each have their own `FanResponseController` with:

- A 3-second sample window.
- Median filtering over samples in that window.
- A 3-percentage-point output deadband.
- A 5-second linear output ramp.
- Immediate reset when the selected configuration changes.

The controller is evaluated by the 300 ms main loop. Reducing the outer loop must preserve the time-based semantics above. The filter is based on elapsed time, not a fixed number of samples, so a moderate reduction in sample rate is compatible, but too few samples would weaken median rejection and make ramp steps visible.

## Output path and write rate

Every 300 ms the service computes `max(applicationOutput, usbOutput)` and calls `FanController.writePercent`, including when the value is unchanged or is zero.

Preferred output discovery searches root-visible hwmon devices for names matching a PWM fan and writes:

```text
/sys/class/hwmon/hwmon*/pwm1
```

The 0–100% output is converted to `0..255`. If no PWM node is found or the write fails, the fallback writes a quantized `0..max_state` value to:

```text
/sys/class/thermal/cooling_device*/cur_state
```

Each write is issued through a libsu command. Current maximum write rate is approximately **3.33 root-shell writes per second**, or **12,000 writes per hour**, even for a stable target.

## Screen-off behavior

Five seconds after `SCREEN_OFF`, the application curve is forced to zero. The USB curve remains active. The loop, all thermal reads, CPU-frequency telemetry, notification updates, and fan writes continue. Consequently a non-charging, screen-off device still repeatedly writes fan-off state.

## Current idle cases

- Application curve disabled, USB disabled: still samples and writes 0 every 300 ms.
- Application temperature below its first working point: still samples and writes 0 every 300 ms.
- Screen off and not charging: still samples and writes 0 every 300 ms.
- Stable nonzero output: still rewrites the same PWM every 300 ms.
- USB enabled but not charging: USB temperature is still sampled and participates in the loop.

## Safety constraints for optimization

- USB thermal control must remain active during charging, including while the screen is off.
- USB sampling can stop when external power is disconnected.
- Application thermal sampling can pause when its curve is disabled or when a safe, explicit below-start condition is maintained, but restart conditions must not depend on a sensor that is no longer being sampled.
- Kernel fan-trip suppression must be verified at service start and after relevant kernel/device lifecycle events.
- A failed ownership verification should use a conservative fallback rather than assuming repeated PWM writes can defeat an unknown kernel policy.
- CPU/GPU thermal throttling and emergency protections must remain independent unless the user explicitly disables them.
