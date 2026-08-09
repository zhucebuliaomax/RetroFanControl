#!/system/bin/sh

PACKAGE=com.mmax.retrocontrol
APP_OP=PROJECT_MEDIA
USER_ID=0

if ! pm path "$PACKAGE" >/dev/null 2>&1; then
    echo "RetroControl is not installed: $PACKAGE"
    exit 1
fi

cmd appops set --user "$USER_ID" "$PACKAGE" "$APP_OP" allow
cmd appops write-settings >/dev/null 2>&1
echo "Current state:"
cmd appops get --user "$USER_ID" "$PACKAGE" "$APP_OP"
