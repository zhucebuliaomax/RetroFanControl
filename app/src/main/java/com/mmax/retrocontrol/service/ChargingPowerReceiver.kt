package com.mmax.retrocontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.ChargingControlPreferences
import com.mmax.retrocontrol.data.ChargeSpeedPreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.hardware.BatteryConnectionReader
import com.mmax.retrocontrol.hardware.ChargeSpeedController
import com.mmax.retrocontrol.tile.ChargingQuickSettingsTile
import com.mmax.retrocontrol.tile.ChargeSpeedQuickSettingsTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Establishes and tears down the charging-control session at cable boundaries. */
class ChargingPowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED &&
            intent.action != Intent.ACTION_POWER_DISCONNECTED
        ) return

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val connected = intent.action == Intent.ACTION_POWER_CONNECTED
        val preserve = ChargingControlPreferences.isPreserveEnabled(prefs)
        if (connected && preserve) {
            ChargingControlPreferences.beginPreservedSession(prefs)
        } else {
            ChargingControlPreferences.resetSession(prefs)
        }
        ChargingQuickSettingsTile.requestRefresh(appContext)
        ChargeSpeedQuickSettingsTile.requestRefresh(appContext)

        val pendingResult = goAsync()
        RootAccessManager.ensureRoot { granted ->
            if (!granted) {
                Log.w(TAG, "Root unavailable while handling power connection change")
                pendingResult.finish()
                return@ensureRoot
            }
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                if (connected) {
                    ChargeSpeedController.setSlowChargingEnabled(
                        ChargeSpeedPreferences.isSlowChargingEnabled(prefs),
                    ).onFailure { Log.e(TAG, "Unable to restore charging speed", it) }
                }
                val broadcastState = BatteryConnectionReader.read(appContext).copy(
                    powerConnected = connected,
                )
                ChargingControlCoordinator.apply(appContext, prefs, broadcastState)
                if (connected && preserve) {
                    runCatching { SystemControlService.updateChargingControl(appContext) }
                        .onFailure { Log.e(TAG, "Unable to start charging control", it) }
                }
                ChargingQuickSettingsTile.requestRefresh(appContext)
                ChargeSpeedQuickSettingsTile.requestRefresh(appContext)
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ChargingPowerReceiver"
    }
}
