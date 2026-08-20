# Joystick Control Runtime

## Purpose and output nodes

The Joystick control owns the eight RP6 joystick RGB LED devices:

```text
/sys/class/leds/left:stick:0..3
/sys/class/leds/right:stick:0..3
```

Color is written to each device's `multi_intensity` node and brightness to `brightness`. The controller caches the last emitted color and brightness for every LED and omits unchanged nodes. A full Rainbow frame therefore contains eight `multi_intensity` sysfs writes after the initial brightness has been established, rather than rewriting all eight brightness nodes on every frame.

The effective joystick profile is resolved on startup, preference changes, preview changes, and foreground-app changes. Screen-off suspends the active effect immediately and writes brightness 0. Unlock restarts the resolved effect.

## Current mode behavior

| Mode | Current update cadence | Work per update |
|---|---:|---|
| Off | One shot | Brightness 0 on all eight LEDs |
| Static | One shot | Color and brightness on all eight LEDs |
| Rainbow | 83 ms target period (about 12 Hz) | Full eight-LED color frame; measured 11.42 Hz on RP6 |
| Breathe | 120 ms (8.3 Hz), plus 400 ms pause at the low end | Same color plus changing brightness on all LEDs |
| Battery | Event driven | Recomputes color on battery broadcasts; cached output suppresses unchanged writes |
| Thermal | Shared 2 s sample | Consumes the service thermal snapshot instead of initiating a duplicate scan |
| Wave | 100 ms (10 Hz) | Full eight-LED frame |
| Color Cycle | 120 ms (8.3 Hz) | Full static update |
| Meteor | 120 ms (8.3 Hz) | Full eight-LED frame |
| Fire | Random 100–159 ms (about 6.3–10 Hz) | Full eight-LED frame |
| Aurora | 100 ms (10 Hz) | Full eight-LED frame |
| Ocean | 100 ms (10 Hz) | Full eight-LED frame |
| Starlight | Random 100–199 ms (about 5–10 Hz) | Full eight-LED frame |
| Ambilight | Frames accepted every 83 ms (up to about 12 Hz) | 16×9 capture, zone sampling, then writes changed LED zones |

Static is already one shot; it is not continuously rewritten. Off is also one shot when entering the state or suspending. Brightness-only changes during an active Ambilight session write brightness once to all LEDs without restarting projection.

## Ambilight

Ambilight uses MediaProjection and a 16×9 `ImageReader`. It samples eight small zones, smooths color, limits each channel to a maximum step of 17 with smoothing alpha 0.2, and only appends a sysfs update for a zone whose output color changed. The command is skipped if no LED changed. Capture callbacks run on a dedicated handler thread rather than the main looper. A persisted Ambilight toggle without a live MediaProjection token is cleared on service restart so it cannot indefinitely suppress the ordinary Joystick profile.

## Thermal mode interaction

Thermal RGB consumes a shared full thermal snapshot at a two-second cadence. When the application fan curve has already produced a full snapshot, the same result is reused; otherwise the service performs the low-rate read required by the Thermal RGB mode.

## RP6 measured CPU and I2C baseline (2026-08-20)

The comparison was captured on the same rooted RP6 with the screen awake, the application in the background, Ambilight disabled, and all non-LED service behavior left unchanged. Rainbow used brightness 42. The control case changed only the effective Joystick profile to Off; the Joystick tile and service remained enabled. CPU percentages below are single-core equivalents accumulated by the process and do not identify a particular physical CPU core.

| Effective profile | Window | Process CPU | User CPU | System CPU | LED frames | HTR3212 I2C writes |
|---|---:|---:|---:|---:|---:|---:|
| Rainbow | 10.07 s | 3.67% | 2.38% | 1.29% | 115 (11.42 Hz) | 3,680 total; 1,840 per controller; 365.4/s |
| Off | 11.30 s | 1.24% | 0.80% | 0.44% | None | Not traced; all eight brightness nodes verified as 0 |
| Rainbow increment | Normalized | 2.43 percentage points | 1.58 points | 0.85 points | — | — |

The HTR3212 driver performs three PWM-register writes plus one update-latch write for each changed LED group. The current full Rainbow frame therefore produces 32 I2C writes. Suppressing the redundant brightness write halves the per-frame transactions from the previous approximately 64 writes.

The Off control sampled the following active thread costs. Values are approximate because the kernel accounts CPU in ticks and threads may appear or disappear within the window.

| Thread group | Single-core equivalent while Off |
|---|---:|
| `DefaultDispatch` threads | 0.44% |
| `pool-3-thread-1/2` | 0.35% |
| `HeapTaskDaemon` | 0.18% |
| `Jit` | 0.09% |
| `DefaultExecutor` | 0.09% |

### Measurement conclusion

- The non-LED service baseline is already about 1.24% of one core.
- Rainbow adds about 2.43 percentage points and accounts for roughly 66% of the process CPU observed with the effect active.
- Achieving 1.0–1.5% for the entire process while a dynamic effect remains active would leave only about 0.26 percentage points above the measured baseline. That requires removing roughly 89% of the current Rainbow increment.
- Color-math and allocation cleanup alone is unlikely to reach that target. The dominant next investigation should isolate the root/sysfs submission cost and evaluate a persistent root writer with pre-opened nodes. A driver-level whole-controller batch interface would be the stronger option if application-side submission remains too expensive.
- The measured 11.3–11.8 Hz range was visually accepted. Further work should preserve this effective cadence instead of pursuing 15 Hz as an independent goal.

## Optimization boundary

Each visual mode should retain a mode-specific cadence rather than sharing one global RGB timer. Recommended targets require visual testing:

- Keep Static and Off one shot.
- Battery and Thermal should write only when the displayed color bucket changes; their sensors can be checked at a low rate.
- Smooth procedural effects can initially be tested at 8–12 Hz instead of up to 20 Hz. Rainbow is currently accepted at an effective 11.3–11.8 Hz.
- Step-like effects such as Meteor can remain near their existing perceptual step rate if lowering it is visibly worse.
- Ambilight should retain change suppression and currently uses an approximately 12 Hz capture/output limit.
- Cache the last emitted state per LED and omit unchanged color or brightness nodes.
- Share a low-rate thermal source with the fan/telemetry coordinator instead of initiating a duplicate full scan.

Screen-off cancellation must remain immediate for every mode.
