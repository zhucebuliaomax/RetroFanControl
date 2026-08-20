# Runtime and Power Optimization Plan

## Goals

1. Keep `SystemControlService` sticky and retain RetroControl as the persistent system-control owner.
2. Preserve USB thermal fan control during screen-off charging.
3. Maintain exclusive application ownership of the fan without high-frequency unchanged writes.
4. Decouple control cadence from presentation telemetry.
5. Make one-shot controls dormant after successful application.
6. Preserve the existing fan median filter, deadband, and ramp semantics.

No implementation is included in this document.

## Proposed runtime coordinators

Split the current combined loop conceptually into independent workloads:

| Workload | Activation | Initial target cadence | Output policy |
|---|---|---:|---|
| Application CPU/GPU fan control | Fan curve enabled and screen interactive | Thermal sample 1 s; response/output tick 500 ms while ramping, otherwise event/sample driven | Write on material target/output change |
| USB thermal fan control | USB thermal enabled and external power connected | 2–5 s | Write when combined required output changes |
| Fan ownership watchdog | Service start and selected lifecycle events | No steady poll initially | Verify trips/modes and repair if needed |
| Dashboard thermal telemetry | Dashboard visibly resumed | 1–2 s | In-process publication only |
| Overlay thermal telemetry | Overlay visible | 1 s | In-process publication only |
| Overlay CPU-frequency telemetry | Overlay visible | 500 ms–1 s | Root batch read only |
| Foreground-app resolution | Screen interactive/unlocked | Immediate on resume, then 2–3 s fallback poll if no event API is selected | Reapply only changed controls |
| Notification | Service start and resolved-control changes | Event driven | Show four selected controls; no temperature fields |

Cadences are starting points for device measurement, not final constants.

## Phase 1: instrumentation and baselines

- Add counters and timing metrics in a debug build for thermal reads, `dumpsys` calls, root commands, fan writes, RGB frames, notification updates, and control reapplications.
- Record screen-on idle, screen-off unplugged, and screen-off charging baselines.
- Use Perfetto and `dumpsys batterystats`; capture external current telemetry if the device exposes a trustworthy battery-current node.
- Record fan response around curve points and USB charging near the normal 45°C operating region.
- Establish acceptance criteria for temperature overshoot, output latency, RGB visual quality, and idle wakeups.

## Phase 2: notification redesign

- Replace fan percentage and CPU/GPU temperature content with the four resolved control names: Fan Curve, Joystick, Button Layout, and Frequency Profile.
- Update only when any resolved control changes, when the service starts, or when localization/configuration requires rebuilding.
- Remove the two-second notification timer.
- Keep the ongoing foreground-service status and existing silent behavior.

Expected result: no periodic NotificationManager work.

## Phase 3: fan ownership and write suppression

- Treat kernel-trip suppression as the ownership mechanism.
- At startup, discover every CPU/GPU/USB trip bound to `pwm-fan`, verify the 125°C suppression and required zone modes, and repair mismatches.
- Verify ownership after boot/service recreation and any known thermal-driver reinitialization event.
- Cache the last successfully written physical PWM/cooling state.
- Write immediately when the requested physical output changes.
- Do not rewrite an unchanged state during normal verified ownership.
- If testing reveals an external writer, identify and disable that owner. Use a low-rate watchdog only as a documented fallback, not a 300 ms permanent rewrite.
- Define a conservative failure state if ownership cannot be verified.

This phase should precede aggressive fan-loop slowing so behavior is deterministic.

## Phase 4: independent USB thermal control

- Separate USB sampling/filter state from CPU/GPU fan sampling and from presentation telemetry.
- Subscribe to battery/power connection state.
- Pause USB temperature sampling when external power is disconnected.
- Start an immediate USB sample when power connects, then use a 2–5 second interval while charging.
- Continue during screen-off charging.
- Stop promptly on disconnect after recomputing the combined fan request.
- Keep an independent filter/deadband state so USB and application curves cannot reset or distort each other.
- Combine the latest valid outputs using `max(applicationOutput, usbOutput)` at one fan-output arbiter.
- Explicitly select the intended USB sensor on RP6 (`usb-therm` versus `usb`) and document fallback classification instead of relying silently on first discovery order.

Because normal USB temperature stays near its threshold, charging state—not distance from threshold—is the primary gating condition.

## Phase 5: application fan cadence without filter regression

- Preserve `FanResponseController` as elapsed-time based.
- Initially sample CPU/GPU temperature once per second. A 3-second median window would then contain roughly four boundary-inclusive samples instead of about seven.
- Evaluate the response/output ramp every 500 ms only while a ramp is active; when stable, evaluate on new temperature samples or configuration changes.
- Write only when the rounded physical PWM/cooling state changes.
- When the application curve is disabled or screen-off suspension is active, stop its CPU/GPU sampling after publishing one final inactive state.
- A below-first-point optimization must use a safe recheck strategy. Fully stopping forever would prevent detection of later heating. Use a low-rate recheck (for example 3–5 seconds) or a kernel thermal event if a reliable one is available.
- Keep USB sampling independent and active whenever charging requires it.
- Test spike rejection, deadband transitions, ramp smoothness, and curve-edit immediate response before selecting final rates.

## Phase 6: telemetry lifecycle separation

- Remove current CPU-frequency reads from the fan controller.
- Enable `scaling_cur_freq` sampling only while the overlay is visible. Use 500 ms–1 second based on visual testing.
- Give dashboard thermal telemetry its own lifecycle-bound, slower stream.
- Let fan controllers publish their latest control temperatures and outputs without forcing display-rate sampling.
- Allow thermal RGB to consume a shared low-rate thermal stream rather than starting a duplicate scan.
- Stop all presentation-only telemetry when no presentation is visible.

## Phase 7: foreground-app detection

`dumpsys` is currently used to obtain Android framework state, not hardware state. Sysfs cannot replace it.

- Pause foreground-app polling on `SCREEN_OFF`.
- On `USER_PRESENT`, resolve once immediately before restoring app-specific controls.
- Investigate event-driven Android options on the target ROM: usage events with the required access, an accessibility-based integration if acceptable, task-stack callbacks available to a privileged/system deployment, or a ROM-specific framework signal.
- If polling remains necessary, reduce it to 2–3 seconds and avoid the second window dumpsys unless the activity query actually fails.
- Benchmark narrower dumpsys forms on the RP6 ROM, but do not assume differently formatted output remains stable across ROM releases.
- Cache parsed state and do no control work when the package is unchanged.

## Phase 8: mode-specific joystick optimization

- Retain one-shot Static and Off behavior.
- Add last-emitted color/brightness state per LED.
- For Battery and Thermal modes, sample slowly and write only when the resulting color changes.
- Test Rainbow and Ambilight at 10–15 Hz; test other procedural modes around 8–10 Hz.
- Preserve higher mode-specific rates only where side-by-side visual testing shows a meaningful benefit.
- Continue cancelling all RGB work immediately on screen-off.
- Measure MediaProjection and main-thread callback cost separately from LED sysfs writes.

## Phase 9: one-shot subsystem cleanup

- Frequency Profile: remain dormant after verified application; current-frequency sampling belongs only to visible telemetry.
- Button Layout: retain event-driven compare/write/verify; avoid unnecessary forced reads on generic service updates.
- Charging threshold: retain battery-event-driven conditional writes and verification.
- Quick Settings tiles: refresh on relevant state changes rather than from unrelated control paths.

## Validation matrix

| Scenario | Required behavior |
|---|---|
| Screen on, fan active | Stable filtered curve response with no unchanged PWM writes |
| Screen on, fan below start point | Low-rate safety recheck, fan remains off, USB independent |
| Screen off, unplugged | App fan and foreground polling paused; USB paused; RGB off |
| Screen off, charging | USB thermal loop active; app fan suspended; no presentation telemetry |
| Plug/unplug while screen off | USB loop starts/stops and combined output updates immediately |
| Foreground app changes | Four controls resolve within the accepted latency and only changed hardware is applied |
| Overlay opens/closes | CPU telemetry starts/stops with visibility |
| Kernel/service restart | Fan-trip ownership is verified and restored before normal control |
| Static RGB | One application only, then zero periodic writes |
| Dynamic RGB | Mode-specific bounded frame rate and unchanged-state suppression |

## Expected priority by benefit

1. Stop unchanged 300 ms fan writes after ownership verification.
2. Pause screen-off foreground `dumpsys` polling.
3. Remove CPU-frequency sampling when the overlay is hidden.
4. Make notification updates event driven.
5. Split charging-gated USB thermal sampling from application fan control.
6. Slow application thermal sampling while preserving the response algorithm.
7. Tune dynamic RGB per mode.
8. Remove redundant forced work from one-shot controls.
