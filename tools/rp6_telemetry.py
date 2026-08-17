#!/usr/bin/env python3
"""Record RP6 battery and USB telemetry through adb every 10 seconds.

Usage:
    python3 tools/rp6_telemetry.py
    python3 tools/rp6_telemetry.py --serial SERIAL --output rp6.csv

Stop with Ctrl-C.  Output is a UTF-8 CSV that can be opened directly in Numbers.
"""

import argparse
import csv
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path


FIELDS = (
    "timestamp_local", "timestamp_utc", "battery_temp_c", "usb_therm_c",
    "usb_hotspot_c", "battery_percent", "battery_voltage_v",
    "battery_current_a", "battery_charge_power_w", "charge_power_w",
    "charge_power_source", "status",
)
THERMAL_TYPES = {"battery": "battery_temp_c", "usb-therm": "usb_therm_c", "usb": "usb_hotspot_c"}


def adb(args, serial=None):
    command = ["adb"] + (["-s", serial] if serial else []) + args
    return subprocess.run(command, text=True, capture_output=True, check=False, timeout=15)


def connected_serial(requested):
    if requested:
        return requested
    result = adb(["devices"])
    devices = [line.split()[0] for line in result.stdout.splitlines() if line.endswith("\tdevice")]
    if len(devices) != 1:
        raise RuntimeError("请用 --serial 指定设备" if devices else "未发现 ADB 设备")
    return devices[0]


def number(text, name):
    match = re.search(rf"^\s*{re.escape(name)}:\s*(-?\d+)", text, re.M)
    return int(match.group(1)) if match else None


def read_sample(serial):
    thermal = adb(["shell", "for z in /sys/class/thermal/thermal_zone*; do "
                   "printf '%s|' \"$(cat $z/type 2>/dev/null)\"; cat $z/temp 2>/dev/null; done"], serial)
    battery = adb(["shell", "dumpsys battery"], serial)
    # Most ROMs restrict these files to root.  When available, they give real power.
    power = adb(["shell", "for p in battery usb; do for f in voltage_now current_now online; do "
                 "printf '%s_%s=' \"$p\" \"$f\"; cat /sys/class/power_supply/$p/$f 2>/dev/null; done; done"], serial)

    row = {field: "" for field in FIELDS}
    for line in thermal.stdout.splitlines():
        if "|" not in line:
            continue
        kind, value = line.split("|", 1)
        if kind in THERMAL_TYPES:
            try:
                temp = int(value.strip()) / 1000
                if -20 <= temp <= 125:
                    row[THERMAL_TYPES[kind]] = f"{temp:.3f}"
            except ValueError:
                pass

    voltage_mv, level = number(battery.stdout, "voltage"), number(battery.stdout, "level")
    status = number(battery.stdout, "status")
    row["battery_percent"] = level if level is not None else ""
    row["battery_voltage_v"] = f"{voltage_mv / 1000:.3f}" if voltage_mv else ""
    row["status"] = status if status is not None else ""

    values = {
        f"{supply}_{field}": int(value)
        for supply, field, value in re.findall(r"(battery|usb)_(voltage_now|current_now|online)=(-?\d+)", power.stdout)
    }
    current_ua = values.get("battery_current_now")
    battery_uv = values.get("battery_voltage_now")
    usb_ua, usb_uv = values.get("usb_current_now"), values.get("usb_voltage_now")
    if current_ua is not None:
        row["battery_current_a"] = f"{current_ua / 1_000_000:.6f}"
    if current_ua is not None and battery_uv is not None:
        row["battery_charge_power_w"] = f"{abs(current_ua * battery_uv) / 1_000_000_000_000:.3f}"
    if usb_ua is not None and usb_uv is not None and values.get("usb_online", 1):
        row["charge_power_w"] = f"{abs(usb_ua * usb_uv) / 1_000_000_000_000:.3f}"
        row["charge_power_source"] = "usb_voltage_x_current"
    elif row["battery_charge_power_w"]:
        row["charge_power_w"] = row["battery_charge_power_w"]
        row["charge_power_source"] = "battery_voltage_x_current"
    else:
        row["charge_power_source"] = "unavailable_to_adb_shell"
    now = datetime.now().astimezone()
    row["timestamp_local"] = now.isoformat(timespec="seconds")
    row["timestamp_utc"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    return row


def main():
    parser = argparse.ArgumentParser(description="Record RP6 telemetry to CSV.")
    parser.add_argument("--serial", help="ADB serial; inferred when exactly one device is connected")
    parser.add_argument("--output", type=Path, default=Path(f"rp6_telemetry_{datetime.now():%Y%m%d_%H%M%S}.csv"))
    parser.add_argument("--interval", type=float, default=10, help="Sample interval in seconds (default: 10)")
    parser.add_argument("--no-adb-root", action="store_true", help="Do not run `adb root` before recording")
    args = parser.parse_args()
    if args.interval <= 0:
        parser.error("--interval 必须大于 0")
    if not shutil.which("adb"):
        sys.exit("找不到 adb；请先安装 Android platform-tools。")
    serial = connected_serial(args.serial)
    if not args.no_adb_root:
        root = adb(["root"], serial)
        if root.returncode == 0:
            adb(["wait-for-device"], serial)
        else:
            print("提示：adb root 不可用，充电功率可能为空。", file=sys.stderr)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    new_file = not args.output.exists() or args.output.stat().st_size == 0
    print(f"记录 RP6 ({serial}) 到 {args.output}，每 {args.interval:g} 秒一次；按 Ctrl-C 停止。")
    with args.output.open("a", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        if new_file:
            writer.writeheader()
        try:
            while True:
                started = time.monotonic()
                try:
                    row = read_sample(serial)
                    writer.writerow(row)
                    handle.flush()
                    print(
                        f"{row['timestamp_local']} BAT {row['battery_temp_c']}°C "
                        f"USB therm {row['usb_therm_c']}°C USB hotspot {row['usb_hotspot_c']}°C "
                        f"{row['battery_percent']}% {row['charge_power_w']}W"
                    )
                except (subprocess.TimeoutExpired, OSError) as error:
                    print(f"采样失败：{error}", file=sys.stderr)
                time.sleep(max(0, args.interval - (time.monotonic() - started)))
        except KeyboardInterrupt:
            print("\n已停止。")


if __name__ == "__main__":
    main()
