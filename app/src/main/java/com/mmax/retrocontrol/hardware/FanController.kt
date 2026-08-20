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

    private data class AppliedPhysicalOutput(val path: String, val value: Int)

    sealed interface WriteResult {
        data class Success(val percent: Int, val changed: Boolean) : WriteResult
        data class Fault(val code: String) : WriteResult
    }

    private const val ERROR_OUTPUT_UNAVAILABLE = "FC-E10"
    private const val ERROR_OUTPUT_WRITE = "FC-E11"
    private const val ERROR_OUTPUT_VERIFY = "FC-E12"

    @Volatile
    private var lastAppliedOutput: AppliedPhysicalOutput? = null

    @Synchronized
    fun invalidateLastAppliedOutput() {
        lastAppliedOutput = null
    }

    @Synchronized
    fun invalidateDiscovery() {
        cachedPath = null
        cachedMaxState = null
        cachedPwmPath = null
        lastAppliedOutput = null
    }
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

    /** Direct cooling-state write retained for diagnostics and compatibility. */
    fun writeState(value: Int): Boolean {
        val path = discoverFanPath() ?: return false
        val maxState = readMaxState().coerceAtLeast(1)
        val clamped = value.coerceIn(0, maxState)
        return Shell.cmd("echo $clamped > $path/cur_state 2>/dev/null").exec().isSuccess
    }

    /** Writes and verifies only changed physical output, with cooling-state fallback. */
    @Synchronized
    fun writePercent(value: Double): WriteResult {
        val percent = value.roundToInt().coerceIn(0, 100)
        val pwmPath = discoverPwmPath()
        if (pwmPath != null) {
            val pwm = (percent / 100.0 * PWM_MAX).roundToInt()
            when (val result = writeVerified(pwmPath, pwm)) {
                PhysicalWriteResult.SUCCESS -> return WriteResult.Success(percent, changed = true)
                PhysicalWriteResult.UNCHANGED -> return WriteResult.Success(percent, changed = false)
                PhysicalWriteResult.VERIFY_FAILED -> {
                    Log.w(TAG, "PWM readback mismatch at $pwmPath")
                }
                PhysicalWriteResult.WRITE_FAILED -> {
                    Log.w(TAG, "Unable to write PWM at $pwmPath")
                }
            }
            cachedPwmPath = null
        }

        val fanPath = discoverFanPath() ?: return WriteResult.Fault(ERROR_OUTPUT_UNAVAILABLE)
        val maxState = readMaxState().coerceAtLeast(1)
        val state = (percent / 100.0 * maxState).roundToInt()
        return when (writeVerified("$fanPath/cur_state", state)) {
            PhysicalWriteResult.SUCCESS -> WriteResult.Success(percent, changed = true)
            PhysicalWriteResult.UNCHANGED -> WriteResult.Success(percent, changed = false)
            PhysicalWriteResult.VERIFY_FAILED -> WriteResult.Fault(ERROR_OUTPUT_VERIFY)
            PhysicalWriteResult.WRITE_FAILED -> WriteResult.Fault(ERROR_OUTPUT_WRITE)
        }
    }

    private enum class PhysicalWriteResult { SUCCESS, UNCHANGED, WRITE_FAILED, VERIFY_FAILED }

    private fun writeVerified(path: String, value: Int): PhysicalWriteResult {
        if (lastAppliedOutput == AppliedPhysicalOutput(path, value)) {
            return PhysicalWriteResult.UNCHANGED
        }
        val result = Shell.cmd(
            "echo $value > $path 2>/dev/null && cat $path 2>/dev/null"
        ).exec()
        if (!result.isSuccess) return PhysicalWriteResult.WRITE_FAILED
        val actual = result.out.lastOrNull()?.trim()?.toIntOrNull()
            ?: return PhysicalWriteResult.VERIFY_FAILED
        if (actual != value) return PhysicalWriteResult.VERIFY_FAILED
        lastAppliedOutput = AppliedPhysicalOutput(path, value)
        return PhysicalWriteResult.SUCCESS
    }

}
