# RetroControl MediaProjection Auto Grant

This KernelSU module grants the `PROJECT_MEDIA` AppOp to
`com.mmax.retrocontrol`. RetroControl still uses Android's standard
MediaProjection API, but its capture request is approved without displaying
the confirmation dialog.

## Install and verify

1. Install `kernelsu-retrocontrol-project-media-v1.0.0.zip` in KernelSU Manager.
2. Reboot, or press the module's **Action** button to apply it immediately.
3. Change a joystick profile to Ambilight in RetroControl.
4. The screen-sharing request should complete without a confirmation dialog.

The current grant is shown by:

```sh
su -c 'cmd appops get --user 0 com.mmax.retrocontrol PROJECT_MEDIA'
```

Expected output contains:

```text
PROJECT_MEDIA: allow
```

The boot-time result is also written to `appop.log` in the installed module
directory. Removing the module restores this AppOp to its default state.

This grant bypasses the user confirmation dialog. It does not create or retain
a MediaProjection token, remove the foreground-service notification, or make
Ambilight automatically resume after RetroControl discards a token.
