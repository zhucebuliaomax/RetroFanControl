# Frequency Profile Control Runtime

## Purpose

The Frequency Profile control limits CPU maximum frequencies. It does not change the governor, GPU policy, display refresh rate, or unrelated thermal behavior.

## Discovery

CPU policies are dynamically discovered under:

```text
/sys/devices/system/cpu/cpufreq/policy[0-9]*
```

Discovery reads affected or related CPU IDs, available frequencies, current minimum/maximum limits, and hardware minimum/maximum limits. Successful discovery is cached in memory.

## Profile application

The effective profile is resolved on service startup, explicit setting/tile changes, and foreground-app changes. The control writes each policy's `scaling_max_freq`. A non-stock profile may first lower `scaling_min_freq` if it exceeds the requested maximum, and makes the maximum node read-only after writing. Stock restoration writes the stock maximum and the remembered minimum.

After applying a profile, the control reads back all maximum-frequency nodes and verifies the request. Identical resolved requests are skipped after successful initialization unless a caller forces reapplication.

This is therefore primarily **event-driven, one-shot control**, not a periodic control loop.

## Current periodic telemetry

Although profile application is one shot, current frequency telemetry is coupled to the fan loop. Every 500 ms the service issues one root-shell command that reads `scaling_cur_freq` for every CPU ID in all discovered policies:

```text
/sys/devices/system/cpu/cpuN/cpufreq/scaling_cur_freq
```

The result is published to `TelemetryRepository` every 300 ms using the most recent sample. This happens whether the overlay is hidden or visible, on screen or off, and even when the active profile is stock/unmanaged.

No control decision depends on current-frequency telemetry. It exists for display and overlay adjustment only.

## Overlay behavior

The overlay displays current CPU frequencies and target maxima and can adjust profile maxima. Those adjustments intentionally trigger profile persistence and application. Current-frequency sampling is useful while this overlay is visible, but it is unnecessary background work otherwise.

## Optimization boundary

The service may remain sticky, while the Frequency Profile subsystem becomes dormant after a verified write. Recommended runtime behavior is:

- Keep policy metadata cached.
- Reapply only on resolved-profile changes, explicit edits, boot/service restoration, or failed verification.
- Sample `scaling_cur_freq` only while the overlay is visible; optionally use a slower lifecycle-bound stream for an open dashboard.
- Stop current-frequency sampling immediately on screen-off or overlay close.
- Do not periodically rewrite `scaling_max_freq` unless device testing proves another owner changes it; if monitoring is required, use a low-frequency verification read rather than a rewrite.
