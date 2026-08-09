package com.mmax.retrocontrol.hardware

import android.content.SharedPreferences
import android.util.Log
import com.topjohnwu.superuser.Shell

/** Selectively enables or suppresses kernel thermal trips bound to the pwm-fan. */
object KernelFanThermalController {
    private const val TAG = "KernelFanThermal"
    private const val THERMAL_BASE = "/sys/class/thermal"
    private const val DISABLED_TRIP_TEMP_MILLIDEGREES = 125_000
    private const val ORIGINAL_TRIP_PREFIX = "kernel_fan_trip_original_v1_"

    private data class FanTrip(
        val zonePath: String,
        val zoneType: String,
        val kind: ThermalKind,
        val tripIndex: Int,
        val currentTemp: Int,
    ) {
        val tempPath: String = "$zonePath/trip_point_${tripIndex}_temp"
        val preferenceKey: String = buildString {
            append(ORIGINAL_TRIP_PREFIX)
            append(zoneType.map { if (it.isLetterOrDigit()) it else '_' }.joinToString(""))
            append('_')
            append(tripIndex)
        }
    }

    /**
     * CPU/GPU fan trips are suppressed by default so the user curve owns the fan. USB fan trips
     * follow the Settings switch. Only trip points whose cooling device is `pwm-fan` are changed;
     * frequency throttling, CPU hotplug, thermal pause, and thermal-zone modes remain enabled.
     */
    @Synchronized
    fun apply(prefs: SharedPreferences, disableUsbFanControl: Boolean): Boolean {
        val trips = discoverFanTrips()
        if (trips.isEmpty()) {
            Log.w(TAG, "No CPU/GPU/USB pwm-fan thermal trips found")
            return false
        }

        val originalEditor = prefs.edit()
        trips.forEach { trip ->
            if (!prefs.contains(trip.preferenceKey) &&
                trip.currentTemp < DISABLED_TRIP_TEMP_MILLIDEGREES
            ) {
                originalEditor.putInt(trip.preferenceKey, trip.currentTemp)
            }
        }
        originalEditor.commit()

        // Recover zones disabled by older builds; individual pwm-fan trips are controlled below.
        trips.map { it.zonePath }.distinct().forEach { zonePath ->
            Shell.cmd("echo enabled > $zonePath/mode 2>/dev/null").exec()
        }

        var success = true
        trips.forEach { trip ->
            val disabled = when (trip.kind) {
                ThermalKind.CPU, ThermalKind.GPU -> true
                ThermalKind.USB -> disableUsbFanControl
                else -> false
            }
            val original = prefs.getInt(trip.preferenceKey, trip.currentTemp)
            val target = if (disabled) DISABLED_TRIP_TEMP_MILLIDEGREES else original
            if (!Shell.cmd("echo $target > ${trip.tempPath} 2>/dev/null").exec().isSuccess) {
                success = false
                Log.w(TAG, "Unable to write ${trip.zoneType} fan trip ${trip.tripIndex}")
            }
        }
        Log.i(
            TAG,
            "Applied pwm-fan thermal policy: CPU/GPU disabled, USB disabled=$disableUsbFanControl",
        )
        return success
    }

    private fun discoverFanTrips(): List<FanTrip> {
        val script = buildString {
            append("for z in $THERMAL_BASE/thermal_zone*; do ")
            append("t=\$(cat \"\$z/type\" 2>/dev/null); ")
            append("case \"\$t\" in *cpu*|*CPU*|*gpu*|*GPU*|*usb*|*USB*) ;; *) continue;; esac; ")
            append("for c in \"\$z\"/cdev[0-9]*; do ")
            append("[ -L \"\$c\" ] || continue; ")
            append("[ \"\$(cat \"\$c/type\" 2>/dev/null)\" = pwm-fan ] || continue; ")
            append("n=\${c##*/cdev}; i=\$(cat \"\$z/cdev\${n}_trip_point\" 2>/dev/null); ")
            append("v=\$(cat \"\$z/trip_point_\${i}_temp\" 2>/dev/null); ")
            append("echo \"\$z|\$t|\$i|\$v\"; done; done")
        }
        return Shell.cmd(script).exec().out.mapNotNull { line ->
            val fields = line.trim().split('|')
            if (fields.size != 4) return@mapNotNull null
            val kind = when (ThermalClassifier.classify(fields[1])) {
                ThermalKind.CPU -> ThermalKind.CPU
                ThermalKind.GPU -> ThermalKind.GPU
                ThermalKind.USB -> ThermalKind.USB
                else -> return@mapNotNull null
            }
            FanTrip(
                zonePath = fields[0],
                zoneType = fields[1],
                kind = kind,
                tripIndex = fields[2].toIntOrNull() ?: return@mapNotNull null,
                currentTemp = fields[3].toIntOrNull() ?: return@mapNotNull null,
            )
        }.distinctBy { Triple(it.zonePath, it.zoneType, it.tripIndex) }
    }
}
