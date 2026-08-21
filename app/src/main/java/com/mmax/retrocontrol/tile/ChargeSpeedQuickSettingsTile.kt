package com.mmax.retrocontrol.tile

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.ChargeSpeedPreferences
import com.mmax.retrocontrol.data.ChargingControlLogic
import com.mmax.retrocontrol.data.ChargingControlPreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.hardware.BatteryConnectionReader
import com.mmax.retrocontrol.hardware.BatteryConnectionState
import com.mmax.retrocontrol.hardware.ChargeSpeedController
import com.topjohnwu.superuser.Shell
import java.lang.ref.WeakReference

/** Tap switches between the RP6 default rapid charge and an approximately 10 W slow charge. */
class ChargeSpeedQuickSettingsTile : TileService() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        listeningInstance = WeakReference(this)
        updateTile()
    }

    override fun onStopListening() {
        if (listeningInstance?.get() === this) listeningInstance = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        if (!isActivelyCharging(prefs, BatteryConnectionReader.read(this))) {
            Toast.makeText(this, R.string.charge_speed_not_charging, Toast.LENGTH_SHORT).show()
            updateTile()
            return
        }

        val previous = ChargeSpeedPreferences.isSlowChargingEnabled(prefs)
        val next = !previous
        ChargeSpeedPreferences.setSlowChargingEnabled(prefs, next)
        updateTile(next)

        RootAccessManager.ensureRoot { granted ->
            if (!granted) {
                restoreAfterFailure(previous, R.string.charge_speed_root_required)
                return@ensureRoot
            }
            Shell.EXECUTOR.execute {
                ChargeSpeedController.setSlowChargingEnabled(next)
                    .onSuccess {
                        mainHandler.post {
                            Toast.makeText(
                                applicationContext,
                                if (next) R.string.charge_speed_slow_toast
                                else R.string.charge_speed_rapid_toast,
                                Toast.LENGTH_SHORT,
                            ).show()
                            requestRefresh(applicationContext)
                        }
                    }
                    .onFailure {
                        mainHandler.post {
                            restoreAfterFailure(previous, R.string.charge_speed_apply_failed)
                        }
                    }
            }
        }
    }

    private fun restoreAfterFailure(previous: Boolean, message: Int) {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        ChargeSpeedPreferences.setSlowChargingEnabled(prefs, previous)
        updateTile(previous)
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        requestRefresh(applicationContext)
    }

    private fun updateTile(
        slowCharging: Boolean = ChargeSpeedPreferences.isSlowChargingEnabled(
            getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
        ),
    ) {
        val battery = BatteryConnectionReader.read(this)
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val charging = isActivelyCharging(prefs, battery)
        qsTile?.apply {
            icon = Icon.createWithResource(
                applicationContext,
                when {
                    !charging -> R.drawable.ic_charge_off
                    slowCharging -> R.drawable.ic_charge_slow
                    else -> R.drawable.ic_charge_rapid
                },
            )
            label = getString(R.string.tile_charge_speed_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(
                    when {
                        !charging -> R.string.charge_speed_not_charging
                        slowCharging -> R.string.charge_speed_slow
                        else -> R.string.charge_speed_rapid
                    },
                )
            }
            state = if (charging) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    private fun isActivelyCharging(
        prefs: SharedPreferences,
        battery: BatteryConnectionState,
    ): Boolean {
        val chargingRequested = ChargingControlLogic.decide(
            ChargingControlPreferences.load(prefs),
            battery.level,
        ).chargingEnabled
        return battery.powerConnected && battery.charging && chargingRequested
    }

    companion object {
        @Volatile
        private var listeningInstance: WeakReference<ChargeSpeedQuickSettingsTile>? = null

        fun requestRefresh(context: Context) {
            listeningInstance?.get()?.let { tile ->
                tile.mainHandler.post { tile.updateTile() }
            }
            requestListeningState(
                context,
                ComponentName(context, ChargeSpeedQuickSettingsTile::class.java),
            )
        }
    }
}
