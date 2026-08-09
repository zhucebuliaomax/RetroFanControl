#!/system/bin/sh

MODDIR=${0%/*}
PACKAGE=com.mmax.retrocontrol
APP_OP=PROJECT_MEDIA
USER_ID=0
LOG_FILE="$MODDIR/appop.log"

# KernelSU starts service.sh asynchronously during late_start. Wait until
# Android is ready before talking to PackageManager and AppOpsService.
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

attempt=0
while ! pm path "$PACKAGE" >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 30 ]; then
        echo "$(date '+%F %T') package not found: $PACKAGE" > "$LOG_FILE"
        exit 1
    fi
    sleep 2
done

if cmd appops set --user "$USER_ID" "$PACKAGE" "$APP_OP" allow; then
    cmd appops write-settings >/dev/null 2>&1
    {
        echo "$(date '+%F %T') granted $APP_OP to $PACKAGE"
        cmd appops get --user "$USER_ID" "$PACKAGE" "$APP_OP"
    } > "$LOG_FILE" 2>&1
else
    echo "$(date '+%F %T') failed to grant $APP_OP to $PACKAGE" > "$LOG_FILE"
    exit 1
fi
