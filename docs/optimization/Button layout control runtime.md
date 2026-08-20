# Button Layout Control Runtime

## Purpose and nodes

The Button Layout control manages four Moorechip joystick driver nodes:

```text
/sys/class/moorechip-joystick/joystick/layout
/sys/class/moorechip-joystick/joystick/m0_function
/sys/class/moorechip-joystick/joystick/m1_function
/sys/class/moorechip-joystick/joystick/triggers
```

These represent face-button labeling/layout, the two rear-button mappings, and trigger mode.

## Application mechanism

The effective layout profile is resolved on service startup, profile/catalog or tile changes, and foreground-app changes.

Application is serialized by a mutex:

1. Read all four current node values in one root-shell command.
2. Compare them with the normalized target.
3. Build writes only for changed fields.
4. Write rear-button mappings first.
5. Write trigger mode and face layout afterward because those changes may re-register the input device.
6. Read all four nodes again and verify the final state.

After a successful request, an identical target is skipped unless a caller explicitly forces reapplication. The module has no periodic sampler and no periodic writer.

## Current frequency

Button Layout is event driven. A normal foreground-app change can cause one read, zero to four writes, and one verification read. Startup and explicit update paths currently use `force = true`, so they perform the read/compare/verify sequence even when the remembered target is unchanged; unchanged individual fields still are not written.

There is no screen-on or screen-off loop specific to this module.

## Optimization boundary

This control is already close to the desired low-power model. Improvements should focus on avoiding redundant forced verification rather than adding timers:

- Preserve the last verified target across ordinary service update calls.
- Force reapply only after boot, driver re-registration, explicit profile edits, or a detected mismatch.
- Consider a driver/device-change signal if the ROM exposes one; otherwise a rare lifecycle verification is preferable to polling.
- Keep readback verification because trigger/layout writes may rebuild the input device and failures affect usability.
