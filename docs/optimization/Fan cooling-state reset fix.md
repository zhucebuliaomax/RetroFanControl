# Fan Cooling-State Reset Fix

## Scope

This change prevents a stale kernel `pwm-fan` cooling state from affecting the
first output of a newly selected RetroControl fan curve and makes the unplugged
screen-off shutdown leave both physical output and kernel cooling demand at a
verified zero. It does not change curve interpolation, filtering, or ramping.

## Problem addressed

RetroControl normally controls the physical fan by writing a percentage-derived
value to:

```text
/sys/class/hwmon/hwmon*/pwm1
```

The Linux thermal framework separately retains a logical state in the matching
`pwm-fan` cooling device:

```text
/sys/class/thermal/cooling_device*/cur_state
```

A direct `pwm1` write does not update `cur_state`. A state such as `8`, selected
before RetroControl took ownership, can therefore remain after the application
has suppressed the fan-bound kernel trip points. On RP6, state 8 corresponds to
PWM 175/255. A later driver or power-state transition can reapply that stale
state even when the newly selected application curve requests a lower speed or
zero.

## Implemented behavior

`FanController.resetCoolingState()` now:

1. Discovers the `pwm-fan` cooling device through the existing discovery path.
2. Writes `0` to its `cur_state` and verifies the value by readback.
3. Clears RetroControl's cached last-applied physical output.

The fan loop calls this reset before applying a curve when either condition is
true:

- The active source changes between the application curve, USB thermal curve,
  and no managed source.
- The revision of the currently selected curve changes.

Clearing the physical-output cache is required because writing `cur_state=0`
can change the hardware PWM behind `FanController`. The following `pwm1` write
must therefore run even when its requested percentage matches the value cached
before the reset.

Failure to reset `cur_state` is logged as a warning. It does not suppress the
subsequent normal curve calculation or PWM write.

`FanController.stopAndResetCoolingState()` is used after the unplugged
screen-off grace period. It invalidates the physical-output cache, forces and
verifies a zero physical fan output, then writes and verifies `cur_state=0`.
Before calling it, the service cancels and joins the fan loop so an in-flight
sample cannot restore a nonzero output after shutdown.

While screen-off is settled, cable changes and unlock are serialized through a
single fan-lifecycle mutex. Connecting power restarts USB thermal control;
disconnecting it stops the loop and repeats the verified one-shot shutdown.

## Behavior intentionally unchanged

- Fan sources remain mutually exclusive. The application curve has priority
  while it is eligible to run; USB thermal control takes over only when the
  application curve cannot run.
- USB thermal control still requires both its setting to be enabled and external
  power to be connected.
- Interactive USB fallback sampling remains on its existing two-second interval.
- Screen-off charging samples USB thermal every one second. Screen-off without
  external power performs no periodic sampling after five seconds.
- Stable unchanged PWM requests retain the existing write-deduplication behavior.
- No wake lock or additional Android permission is introduced.
- The telemetry overlay is disabled immediately on screen-off and its Quick
  Settings tile is refreshed to the inactive state.

## Verification

The change was compiled and checked with:

```text
./gradlew :app:testDebugUnitTest
```

The task completed successfully. No APK assembly or installation was performed.
