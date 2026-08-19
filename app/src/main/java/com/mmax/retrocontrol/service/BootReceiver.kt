package com.mmax.retrocontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.FanCurvePreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.data.ChargingControlPreferences
import com.mmax.retrocontrol.hardware.BatteryConnectionReader
import com.mmax.retrocontrol.tile.FanQuickSettingsTile
import com.mmax.retrocontrol.tile.OverlayTileService
import com.mmax.retrocontrol.tile.ButtonLayoutQuickSettingsTile
import com.mmax.retrocontrol.tile.ChargingQuickSettingsTile

/**
 * Restores hardware profiles after boot only when the user opted in.
 * The overlay is always left off after a reboot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

        // Overlay visibility is intentionally never restored across a reboot.
        prefs.edit { putBoolean(Prefs.OVERLAY_ENABLED, false) }
        OverlayTileService.requestRefresh(appContext)
        ButtonLayoutQuickSettingsTile.requestRefresh(appContext)

        ChargingControlPreferences.resetSession(prefs)
        val restoreChargingThreshold =
            ChargingControlPreferences.isPreserveEnabled(prefs) &&
                BatteryConnectionReader.read(appContext).powerConnected
        if (restoreChargingThreshold) {
            ChargingControlPreferences.beginPreservedSession(prefs)
        }
        ChargingQuickSettingsTile.requestRefresh(appContext)

        if (!prefs.getBoolean(Prefs.AUTO_START_ENABLED, true) && !restoreChargingThreshold) {
            FanCurvePreferences.select(prefs, null)
            FanQuickSettingsTile.requestRefresh(appContext)
            Log.i(TAG, "Boot detected — automatic start is disabled")
            pendingResult.finish()
            return
        }

        Log.i(TAG, "Boot detected — preparing automatic control-service start")
        RootAccessManager.ensureRoot { granted ->
            val started = granted && runCatching {
                SystemControlService.startOrUpdate(appContext)
            }.onFailure { error ->
                Log.e(TAG, "Unable to start fan control after boot", error)
            }.isSuccess

            if (!started) {
                FanCurvePreferences.select(prefs, null)
                if (!granted) Log.w(TAG, "Root access unavailable after boot")
            }
            FanQuickSettingsTile.requestRefresh(appContext)
            OverlayTileService.requestRefresh(appContext)
            ButtonLayoutQuickSettingsTile.requestRefresh(appContext)
            ChargingQuickSettingsTile.requestRefresh(appContext)
            pendingResult.finish()
        }
    }
}
