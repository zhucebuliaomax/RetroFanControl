# Joystick Control Runtime

## Purpose and output nodes

The Joystick control owns the eight RP6 joystick RGB LED devices:

```text
/sys/class/leds/left:stick:0..3
/sys/class/leds/right:stick:0..3
```

Color is written to each device's `multi_intensity` node and brightness to `brightness`. A frame is batched into one libsu shell command, but that command contains up to sixteen sysfs writes for a full eight-LED color/brightness update.

The effective joystick profile is resolved on startup, preference changes, preview changes, and foreground-app changes. Screen-off suspends the active effect immediately and writes brightness 0. Unlock restarts the resolved effect.

## Current mode behavior

| Mode | Current update cadence | Work per update |
|---|---:|---|
| Off | One shot | Brightness 0 on all eight LEDs |
| Static | One shot | Color and brightness on all eight LEDs |
| Rainbow | 50 ms (20 Hz) | Full eight-LED frame |
| Breathe | 120 ms (8.3 Hz), plus 400 ms pause at the low end | Same color plus changing brightness on all LEDs |
| Battery | 2 s (0.5 Hz) | Battery percentage read and full static update |
| Thermal | 2 s (0.5 Hz) | Independent full thermal-zone scan and full static update |
| Wave | 100 ms (10 Hz) | Full eight-LED frame |
| Color Cycle | 120 ms (8.3 Hz) | Full static update |
| Meteor | 120 ms (8.3 Hz) | Full eight-LED frame |
| Fire | Random 100–159 ms (about 6.3–10 Hz) | Full eight-LED frame |
| Aurora | 100 ms (10 Hz) | Full eight-LED frame |
| Ocean | 100 ms (10 Hz) | Full eight-LED frame |
| Starlight | Random 100–199 ms (about 5–10 Hz) | Full eight-LED frame |
| Ambilight | Frames accepted every 50 ms (up to 20 Hz) | 16×9 capture, zone sampling, then writes changed LED zones |

Static is already one shot; it is not continuously rewritten. Off is also one shot when entering the state or suspending. Brightness-only changes during an active Ambilight session write brightness once to all LEDs without restarting projection.

## Ambilight

Ambilight uses MediaProjection and a 16×9 `ImageReader`. It samples eight small zones, smooths color, limits per-channel changes, and only appends a sysfs update for a zone whose output color changed. The command is skipped if no LED changed. The capture callback runs on the main looper.

## Thermal mode interaction

Thermal RGB performs its own `ThermalSensorReader.read()` every two seconds rather than consuming the service's latest telemetry. This avoids depending on UI state but duplicates sensor scanning while the main fan loop is also sampling.

## Optimization boundary

Each visual mode should retain a mode-specific cadence rather than sharing one global RGB timer. Recommended targets require visual testing:

- Keep Static and Off one shot.
- Battery and Thermal should write only when the displayed color bucket changes; their sensors can be checked at a low rate.
- Smooth procedural effects can initially be tested at 8–12 Hz instead of up to 20 Hz.
- Step-like effects such as Meteor can remain near their existing perceptual step rate if lowering it is visibly worse.
- Ambilight should retain change suppression and can test 10–15 Hz capture/output limits.
- Cache the last emitted state per LED and omit unchanged color or brightness nodes.
- Share a low-rate thermal source with the fan/telemetry coordinator instead of initiating a duplicate full scan.

Screen-off cancellation must remain immediate for every mode.
