package com.mmax.retrocontrol.hardware

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlin.math.roundToInt

/** Writes only pwm-fan output controls. It never changes thermal protection. */
object FanController {
    private const val TAG = "FanController"
    private const val THERMAL_BASE = "/sys/class/thermal"
    private const val HWMON_BASE = "/sys/class/hwmon"
    private const val PWM_MAX = 255

    @Volatile
    private var cachedPath: String? = null
    @Volatile
    private var cachedMaxState: Int? = null
    @Volatile
    private var cachedPwmPath: String? = null

    @Volatile
    private var thermalOverrideActive = false

    fun discoverPwmPath(): String? {
        cachedPwmPath?.let { return it }
        val script = buildString {
            append("for h in $HWMON_BASE/hwmon*; do ")
            append("n=\$(cat \"\$h/name\" 2>/dev/null); ")
            append("case \"\$n\" in pwmfan|pwm-fan|*pwm*fan*) ")
            append("[ -e \"\$h/pwm1\" ] && echo \"\$h/pwm1\" && break;; ")
            append("esac; done")
        }
        val path = Shell.cmd(script).exec().out.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (path != null) {
            Log.i(TAG, "Found pwm-fan PWM control at $path")
            cachedPwmPath = path
        }
        return path
    }

    fun discoverFanPath(): String? {
        cachedPath?.let { return it }
        val script = buildString {
            append("for d in $THERMAL_BASE/cooling_device*; do ")
            append("t=\$(cat \"\$d/type\" 2>/dev/null); ")
            append("if [ \"\$t\" = \"pwm-fan\" ]; then echo \"\$d\"; break; fi; ")
            append("done")
        }
        val path = Shell.cmd(script).exec().out.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (path == null) {
            Log.w(TAG, "pwm-fan cooling device not found")
        } else {
            Log.i(TAG, "Found pwm-fan at $path")
            cachedPath = path
        }
        return path
    }

    fun readMaxState(): Int {
        cachedMaxState?.let { return it }
        val path = discoverFanPath() ?: return 8
        val value = Shell.cmd("cat $path/max_state").exec().out.firstOrNull()
            ?.trim()?.toIntOrNull() ?: 8
        cachedMaxState = value
        return value
    }

    fun readCurState(): Int {
        val path = discoverFanPath() ?: return 0
        return Shell.cmd("cat $path/cur_state").exec().out.firstOrNull()
            ?.trim()?.toIntOrNull() ?: 0
    }

    /**
     * Rewrites the requested state every control tick. The kernel step_wise
     * governor may replace cur_state in roughly one second, so suppressing
     * identical writes would make manual fan control unreliable.
     */
    fun writeState(value: Int) {
        val path = discoverFanPath() ?: return
        val maxState = readMaxState().coerceAtLeast(1)
        val clamped = value.coerceIn(0, maxState)
        Shell.cmd("echo $clamped > $path/cur_state 2>/dev/null").exec()
    }

    /**
     * Applies direct PWM while the kernel has no thermal cooling demand. A non-zero cooling
     * state remains owned by the kernel so manual curves cannot fight or undercut hardware
     * thermal protection. If direct PWM is unavailable, use one deterministic cooling state.
     */
    fun writePercent(value: Double): Int {
        val percent = value.roundToInt().coerceIn(0, 100)
        val pwmPath = discoverPwmPath()
        if (pwmPath != null) {
            val thermalState = readCurState()
            if (thermalState > 0) {
                if (!thermalOverrideActive) {
                    Log.i(
                        TAG,
                        "Kernel thermal cooling is active at state $thermalState; " +
                            "leaving PWM under thermal control",
                    )
                    thermalOverrideActive = true
                }
                return readPwmPercent(pwmPath)
            }
            if (thermalOverrideActive) {
                Log.i(TAG, "Kernel thermal cooling released; resuming fan-curve PWM control")
                thermalOverrideActive = false
            }
            val pwm = (percent / 100.0 * PWM_MAX).roundToInt()
            if (Shell.cmd("echo $pwm > $pwmPath 2>/dev/null").exec().isSuccess) {
                return percent
            }
        }

        val maxState = readMaxState().coerceAtLeast(1)
        val state = (percent / 100.0 * maxState).roundToInt()
        writeState(state)
        return percent
    }

    private fun readPwmPercent(path: String): Int {
        val pwm = Shell.cmd("cat $path").exec().out.firstOrNull()
            ?.trim()
            ?.toIntOrNull()
            ?.coerceIn(0, PWM_MAX)
        return if (pwm == null) {
            val maxState = readMaxState().coerceAtLeast(1)
            (readCurState().toDouble() / maxState * 100.0).roundToInt()
        } else {
            (pwm.toDouble() / PWM_MAX * 100.0).roundToInt()
        }
    }
}
