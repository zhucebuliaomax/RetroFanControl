package com.mmax.retrocontrol.hardware

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BatteryConnectionState(
    val powerConnected: Boolean,
    val level: Int,
)

object BatteryConnectionReader {
    fun read(context: Context): BatteryConnectionState {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percentage = if (level >= 0 && scale > 0) level * 100 / scale else 0
        return BatteryConnectionState(
            powerConnected = plugged != 0,
            level = percentage.coerceIn(0, 100),
        )
    }
}

object ChargingController {
    internal const val CHARGING_ENABLED_PATH = "/sys/class/qcom-battery/charging_enabled"
    private val writeMutex = Mutex()

    suspend fun setChargingEnabled(enabled: Boolean): Result<Boolean> = runCatching {
        writeMutex.withLock {
            val requested = if (enabled) "1" else "0"
            val result = Shell.cmd(
                "current=\$(cat $CHARGING_ENABLED_PATH 2>/dev/null); " +
                    "if [ \"\$current\" != \"$requested\" ]; then " +
                    "echo $requested > $CHARGING_ENABLED_PATH; fi; " +
                    "cat $CHARGING_ENABLED_PATH 2>/dev/null",
            ).exec()
            check(result.isSuccess) {
                result.err.joinToString().ifBlank { "Unable to control battery charging" }
            }
            val actual = result.out.lastOrNull()?.trim()
            check(actual == requested) {
                "Charging verification failed: requested=$requested actual=$actual"
            }
            enabled
        }
    }
}
