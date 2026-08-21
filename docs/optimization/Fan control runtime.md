# Fan Curve Control Runtime

## Ownership model

RetroControl has two curve inputs and selects exactly one eligible source:

1. The active application fan curve, driven by CPU/GPU temperature, has priority
   while the device is interactive.
2. The USB thermal curve, driven by USB temperature, is selected only when the
   application curve cannot run and external power is connected.

`KernelFanThermalController` discovers CPU, GPU, and USB thermal trips whose cooling device type is `pwm-fan`. It stores original trip temperatures, temporarily disables each affected zone, raises the fan-bound trips to 125°C, and restores the intended zone mode. CPU/GPU zones normally remain enabled so unrelated throttling and protection continue to work; USB zones are disabled because RetroControl owns their fan policy. A user option can disable broader CPU/GPU thermal protection, but that is separate from fan ownership.

On the RP6 verified on 2026-08-20, the relevant fan trips were at 125°C and the USB fan zone was disabled. The kernel therefore should not overwrite the application-owned fan state during normal operation. Rewriting an unchanged PWM every 300 ms is not needed as the primary ownership mechanism on this configuration.

## Temperature input

Thermal-zone discovery is dynamic and cached. Zone `type` values are classified as CPU, GPU, DDR, battery, USB, or ignored. Each sample still opens every selected zone's `temp` file.

The application curve uses the hotter of:

- Average temperature of all discovered CPU readings.
- Average temperature of all discovered GPU readings.

It does not use the hottest individual sensor. The USB curve uses the first classified USB reading. On the verified device this ordering makes `usb-therm` the likely USB control input, while a second `usb` zone also exists.

Current application temperature sampling interval is **1 second** during ordinary
interactive use, **500 ms** while the telemetry overlay is visible, and **3
seconds** for the safe below-curve recheck. USB thermal sampling is **2 seconds**
while it is the selected interactive fallback and **1 second** during screen-off
charging. Five seconds after screen-off, no thermal sensor is sampled if external
power is disconnected.

## Curve and response algorithm

Fan curves use linear interpolation between temperature/output points. Temperatures below the first point produce 0%. The final point's output is held above the final temperature.

The application and USB paths each have their own `FanResponseController` with:

- A 3-second sample window.
- Median filtering over samples in that window.
- A 3-percentage-point output deadband.
- A 5-second linear output ramp.
- Immediate reset when the selected configuration changes.

The controller is evaluated by the 300 ms main loop while fan control is active.
Ramp output is reevaluated every 500 ms normally or 300 ms while the overlay is
visible. The filter is based on elapsed time, not a fixed number of samples.

## Output path and write rate

The service selects one eligible source: the application curve has priority while
interactive; USB thermal takes over when the application curve is suspended or
unavailable and external power is connected. `FanController.writePercent` caches
the last verified physical output and skips unchanged writes.

Preferred output discovery searches root-visible hwmon devices for names matching a PWM fan and writes:

```text
/sys/class/hwmon/hwmon*/pwm1
```

The 0–100% output is converted to `0..255`. If no PWM node is found or the write fails, the fallback writes a quantized `0..max_state` value to:

```text
/sys/class/thermal/cooling_device*/cur_state
```

Each changed output is issued through a libsu command and verified by readback.

## Screen-off behavior

`SCREEN_OFF` immediately disables the telemetry overlay, persists its disabled
setting, and refreshes the overlay Quick Settings tile. After a five-second grace
period, foreground-app polling and presentation telemetry stop.

If external power is disconnected, the fan loop is cancelled and awaited before
the service performs a one-shot verified shutdown: physical PWM is written to zero
and the `pwm-fan` cooling device `cur_state` is written and read back as zero. CPU,
GPU, USB, CPU-frequency, joystick-thermal, and foreground-app sampling are then
dormant, leaving only event receivers able to wake the service.

If external power is connected, the application curve remains suspended and USB
thermal control continues at the normal one-second interactive sampling cadence.
CPU-frequency and foreground-app sampling remain stopped. Connecting power while
already suspended starts an immediate USB sample; disconnecting power cancels the
fan loop and performs the verified zero-output shutdown. Unlock restores the
interactive samplers.

## Current idle cases

- Application curve disabled and USB ineligible: no thermal samples; zero is
  written only when entering the unmanaged state.
- Application temperature safely below its first working point: rechecked every
  3 seconds.
- Screen off and not charging: all periodic sampling stops after five seconds.
- Stable nonzero output: unchanged physical writes are skipped.
- USB enabled but not charging: USB temperature is not sampled.

## Safety constraints for optimization

- USB thermal control must remain active during charging, including while the screen is off.
- USB sampling can stop when external power is disconnected.
- Application thermal sampling can pause when its curve is disabled or when a safe, explicit below-start condition is maintained, but restart conditions must not depend on a sensor that is no longer being sampled.
- Kernel fan-trip suppression must be verified at service start and after relevant kernel/device lifecycle events.
- A failed ownership verification should use a conservative fallback rather than assuming repeated PWM writes can defeat an unknown kernel policy.
- CPU/GPU thermal throttling and emergency protections must remain independent unless the user explicitly disables them.
