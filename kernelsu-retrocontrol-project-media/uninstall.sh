#!/system/bin/sh

PACKAGE=com.mmax.retrocontrol
APP_OP=PROJECT_MEDIA
USER_ID=0

if pm path "$PACKAGE" >/dev/null 2>&1; then
    cmd appops set --user "$USER_ID" "$PACKAGE" "$APP_OP" default
    cmd appops write-settings >/dev/null 2>&1
fi
