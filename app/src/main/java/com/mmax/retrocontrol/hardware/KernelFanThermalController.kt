package com.mmax.retrocontrol.hardware

import android.content.SharedPreferences
import android.util.Log
import com.mmax.retrocontrol.data.Prefs
import com.topjohnwu.superuser.Shell

/** Selectively enables or suppresses kernel thermal trips bound to the pwm-fan. */
object KernelFanThermalController {
    private const val TAG = "KernelFanThermal"
    private const val THERMAL_BASE = "/sys/class/thermal"
    private const val DISABLED_TRIP_TEMP_MILLIDEGREES = 125_000

    sealed interface ApplyResult {
        data object Success : ApplyResult
        data class Fault(val code: String) : ApplyResult
    }

    private const val ERROR_NO_TRIPS = "FC-E01"
    private const val ERROR_BACKUP = "FC-E02"
    private const val ERROR_DISABLE_ZONE = "FC-E03"
    private const val ERROR_WRITE_TRIP = "FC-E04"
    private const val ERROR_RESTORE_MODE = "FC-E05"
    private const val ERROR_VERIFY = "FC-E06"

    private data class FanTrip(
        val zonePath: String,
        val zoneType: String,
        val kind: ThermalKind,
        val tripIndex: Int,
        val currentTemp: Int,
    ) {
        val tempPath: String = "$zonePath/trip_point_${tripIndex}_temp"
        val preferenceKey: String = buildString {
            append(Prefs.KERNEL_FAN_TRIP_ORIGINAL_PREFIX)
            append(zoneType.map { if (it.isLetterOrDigit()) it else '_' }.joinToString(""))
            append('_')
            append(tripIndex)
        }
    }

    /**
     * CPU/GPU/USB pwm-fan trips are suppressed so application curves exclusively own the fan.
     * CPU/GPU frequency throttling, hotplug, thermal pause, and other thermal zones remain intact.
     */
    @Synchronized
    fun apply(
        prefs: SharedPreferences,
        disableThermalProtection: Boolean,
    ): ApplyResult {
        val trips = discoverFanTrips()
        if (trips.isEmpty()) {
            Log.w(TAG, "No CPU/GPU/USB pwm-fan thermal trips found")
            return ApplyResult.Fault(ERROR_NO_TRIPS)
        }

        val originalEditor = prefs.edit()
        trips.forEach { trip ->
            if (!prefs.contains(trip.preferenceKey) &&
                trip.currentTemp < DISABLED_TRIP_TEMP_MILLIDEGREES
            ) {
                originalEditor.putInt(trip.preferenceKey, trip.currentTemp)
            }
        }
        if (!originalEditor.commit()) {
            Log.w(TAG, "Unable to persist original pwm-fan trip temperatures")
            return ApplyResult.Fault(ERROR_BACKUP)
        }

        val zones = trips.groupBy { it.zonePath }
        val targetModes = zones.mapValues { (_, zoneTrips) ->
            val kind = zoneTrips.first().kind
            if (kind == ThermalKind.USB) {
                "disabled"
            } else if (
                disableThermalProtection && kind in setOf(ThermalKind.CPU, ThermalKind.GPU)
            ) {
                "disabled"
            } else {
                "enabled"
            }
        }

        var firstError: String? = null
        zones.forEach { (zonePath, zoneTrips) ->
            if (!setZoneMode(zonePath, zoneTrips.first().zoneType, "disabled")) {
                if (firstError == null) firstError = ERROR_DISABLE_ZONE
            }
        }
        trips.forEach { trip ->
            val disabled = trip.kind in setOf(
                ThermalKind.CPU,
                ThermalKind.GPU,
                ThermalKind.USB,
            )
            val original = prefs.getInt(trip.preferenceKey, trip.currentTemp)
            val target = if (disabled) DISABLED_TRIP_TEMP_MILLIDEGREES else original
            if (!Shell.cmd("echo $target > ${trip.tempPath} 2>/dev/null").exec().isSuccess) {
                if (firstError == null) firstError = ERROR_WRITE_TRIP
                Log.w(TAG, "Unable to write ${trip.zoneType} fan trip ${trip.tripIndex}")
            }
        }
        zones.forEach { (zonePath, zoneTrips) ->
            if (!setZoneMode(
                    zonePath,
                    zoneTrips.first().zoneType,
                    targetModes.getValue(zonePath),
                )
            ) {
                if (firstError == null) firstError = ERROR_RESTORE_MODE
            }
        }

        if (!verify(trips, targetModes)) {
            if (firstError == null) firstError = ERROR_VERIFY
        }

        firstError?.let { return ApplyResult.Fault(it) }
        FanController.invalidateLastAppliedOutput()
        Log.i(
            TAG,
            "Applied thermal policy: CPU/GPU/USB fan disabled, " +
                "protection disabled=$disableThermalProtection",
        )
        return ApplyResult.Success
    }

    private fun setZoneMode(zonePath: String, zoneType: String, mode: String): Boolean {
        val success = Shell.cmd("echo $mode > $zonePath/mode 2>/dev/null").exec().isSuccess
        if (!success) Log.w(TAG, "Unable to set $zoneType mode to $mode")
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

    private fun verify(
        trips: List<FanTrip>,
        targetModes: Map<String, String>,
    ): Boolean {
        val tripsMatch = trips.all { trip ->
            Shell.cmd("cat ${trip.tempPath} 2>/dev/null").exec().out
                .lastOrNull()?.trim()?.toIntOrNull() == DISABLED_TRIP_TEMP_MILLIDEGREES
        }
        val modesMatch = targetModes.all { (zonePath, expected) ->
            Shell.cmd("cat $zonePath/mode 2>/dev/null").exec().out
                .lastOrNull()?.trim() == expected
        }
        if (!tripsMatch || !modesMatch) {
            Log.w(TAG, "pwm-fan ownership readback verification failed")
        }
        return tripsMatch && modesMatch
    }
}
