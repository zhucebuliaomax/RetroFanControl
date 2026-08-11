# RP6 Kernel Fan Thermal-Control Reference

## Scope and source

This document records the thermal configuration from the device tree currently running on a Retroid Pocket 6 (RP6). It is intended as a reference for fan control, temperature telemetry, and debugging.

- Platform: Qualcomm SM8550 (Snapdragon 8 Gen 2)
- Fan node: `/sys/class/hwmon/hwmon0`, named `pwmfan`
- Thermal cooling device: `/sys/class/thermal/cooling_device34`, type `pwm-fan`
- Kernel thermal governor: `step_wise`
- Source: the running device's `/sys/firmware/fdt`, not an inference based solely on runtime sysfs values.

Device-tree configuration can change with a kernel or firmware update. Export and check the current FDT again before relying on or changing these thresholds.

## Fan hardware output

The fan uses the standard `pwm-fan` driver. The device tree sets a 50,000 ns PWM period, or 20 kHz. `cooling-levels` defines nine cooling states, from 0 through 8. State 0 turns the fan off; state 8 is intentionally below full PWM duty.

| Cooling state | PWM value (/255) | Duty cycle |
|---:|---:|---:|
| 0 | 0 | 0.0% |
| 1 | 40 | 15.7% |
| 2 | 65 | 25.5% |
| 3 | 75 | 29.4% |
| 4 | 90 | 35.3% |
| 5 | 100 | 39.2% |
| 6 | 120 | 47.1% |
| 7 | 150 | 58.8% |
| 8 | 175 | 68.6% |

The highest automatic kernel level is therefore 175/255, not 255/255. An application that writes `pwm1` directly must account for the thermal framework managing the same physical output; a written value is not necessarily persistent.

## Thermal-control model

The same `pwm-fan` cooling device is bound to CPU, GPU, and USB thermal zones. The kernel uses the `step_wise` governor: when any zone crosses a trip point, it requests the corresponding fan state. Because the fan is shared, the effective state should be treated as the highest cooling demand among all active thermal-zone requests.

Each cooling map declares `cooling-device = <&pwm_fan min_state max_state>`. The state ranges below are taken directly from the running device tree.

### GPU: `gpuss-0` and `gpuss-1`

The two GPU zones use the same curve, with a 5°C hysteresis for every stage. GPU is the earliest SoC source to enable the fan.

| Temperature reached | Fan state range | Maximum duty cycle | Step-down threshold |
|---:|---:|---:|---:|
| 50°C | 0–1 | 15.7% | 45°C |
| 55°C | 1–2 | 25.5% | 50°C |
| 60°C | 2–3 | 29.4% | 55°C |
| 65°C | 3–4 | 35.3% | 60°C |
| 70°C | 4–5 | 39.2% | 65°C |
| 75°C | 5–6 | 47.1% | 70°C |
| 80°C | 6–7 | 58.8% | 75°C |
| 85°C | 7–8 | 68.6% | 80°C |

GPU also has a separate `devfreq-3d00000.qcom,kgsl-3d0` frequency-throttling cooling-device binding at 95°C. The fan curve is the active-cooling layer; GPU frequency throttling is a higher-temperature protection layer.

### CPU: `cpu-1-9` and `cpu-1-10`

The two CPU on-die zones use the same curve, again with 5°C hysteresis per stage. CPU starts 5°C later than GPU and begins with the state 1–2 range rather than the GPU's 0–1 range.

| Temperature reached | Fan state range | Maximum duty cycle | Step-down threshold |
|---:|---:|---:|---:|
| 55°C | 1–2 | 25.5% | 50°C |
| 60°C | 2–3 | 29.4% | 55°C |
| 65°C | 3–4 | 35.3% | 60°C |
| 70°C | 4–5 | 39.2% | 65°C |
| 75°C | 5–6 | 47.1% | 70°C |
| 80°C | 6–7 | 58.8% | 75°C |
| 85°C | 7–8 | 68.6% | 80°C |

CPU retains protection measures independent of the fan: thermal pause at 108°C and `cpu-hotplug7` at 110°C. Applications must not disable or replace these protections.

### USB: `usb-therm`

The USB thermal sensor has a more aggressive cooling policy, intended to protect the Type-C/USB area and nearby power circuitry.

| Temperature reached | Fan state range | Maximum duty cycle | Step-down threshold |
|---:|---:|---:|---:|
| 43°C | 6–7 | 58.8% | 41°C |
| 45°C | 7–8 | 68.6% | 42°C |

## Relationship to RetroControl fan control

The tables above describe the stock kernel policy. While RetroControl's service is active, it suppresses only the CPU/GPU/USB trip points bound to `pwm-fan`, disables the fan-only `usb-therm` zone, and owns the fan output through `pwm1`. CPU/GPU frequency throttling, hotplug, thermal pause, and other protection mechanisms remain independent.

RetroControl reproduces the stock USB fan thresholds as an editable app-owned curve. The service evaluates that curve only from `usb-therm`, evaluates the active app curve from CPU/GPU temperature, and writes the higher requested output. This avoids simultaneous kernel and app writers for the same physical PWM output.

## Read-only validation commands

Show fan state:

```shell
adb shell 'cat /sys/class/thermal/cooling_device34/type; cat /sys/class/thermal/cooling_device34/cur_state; cat /sys/class/thermal/cooling_device34/max_state; cat /sys/class/hwmon/hwmon0/pwm1'
```

Show the current temperatures of the key heat sources:

```shell
adb shell 'for z in 14 15 33 34 53; do d=/sys/class/thermal/thermal_zone$z; echo "$(cat $d/type): $(cat $d/temp) m°C"; done'
```

Show trip points and cooling-device bindings for a zone:

```shell
adb shell 'z=/sys/class/thermal/thermal_zone33; for p in $z/trip_point_*_temp; do i=${p#*trip_point_}; i=${i%_temp}; echo "trip $i: $(cat $p) $(cat $z/trip_point_${i}_type)"; done; ls -l $z/cdev*'
```

## Known limits

- `cpu-1-9` and `cpu-1-10` are Qualcomm TSENS internal sensor names. The current runtime configuration does not expose a one-to-one mapping from these names to Linux logical CPU IDs.
- The minimum and maximum state in `cooling-device` are the ranges permitted by the device tree. The exact value chosen at a given instant still depends on the `step_wise` state machine and concurrent requests from other thermal zones.
- This document describes the stock kernel policy. Do not modify thermal trip points, disable cooling devices, or remove CPU/GPU protection bindings to pursue higher performance.
