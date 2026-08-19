package com.mmax.retrocontrol.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.ChargingControlPreferences
import com.mmax.retrocontrol.data.ChargingMode
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.hardware.BatteryConnectionReader
import com.mmax.retrocontrol.service.SystemControlService

/** Tap cycles the per-connection charging mode; long-press adjusts its threshold. */
class ChargingQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val battery = BatteryConnectionReader.read(this)
        if (!battery.powerConnected) {
            Toast.makeText(this, R.string.charging_not_connected, Toast.LENGTH_SHORT).show()
            updateTile()
            return
        }

        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val state = ChargingControlPreferences.selectNext(prefs)
        Toast.makeText(this, state.mode.toastText(state.threshold), Toast.LENGTH_SHORT).show()
        updateTile()
        RootAccessManager.ensureRoot { granted ->
            if (granted) {
                runCatching { SystemControlService.updateChargingControl(applicationContext) }
            }
            requestRefresh(applicationContext)
        }
    }

    private fun updateTile() {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val control = ChargingControlPreferences.load(prefs)
        val battery = BatteryConnectionReader.read(this)
        qsTile?.apply {
            icon = Icon.createWithResource(
                applicationContext,
                iconResource(battery.level, battery.powerConnected, control.mode),
            )
            label = getString(R.string.tile_charging_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (!battery.powerConnected) {
                    getString(R.string.charging_not_connected_short)
                } else {
                    control.mode.subtitleText(control.threshold)
                }
            }
            state = if (battery.powerConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    private fun ChargingMode.toastText(threshold: Int): String = when (this) {
        ChargingMode.NORMAL -> getString(R.string.charging_mode_normal_toast)
        ChargingMode.BYPASS -> getString(R.string.charging_mode_bypass_toast)
        ChargingMode.THRESHOLD -> getString(R.string.charging_mode_threshold_toast, threshold)
    }

    private fun ChargingMode.subtitleText(threshold: Int): String = when (this) {
        ChargingMode.NORMAL -> getString(R.string.charging_mode_normal)
        ChargingMode.BYPASS -> getString(R.string.charging_mode_bypass)
        ChargingMode.THRESHOLD -> getString(R.string.charging_mode_threshold, threshold)
    }

    private fun iconResource(level: Int, connected: Boolean, mode: ChargingMode): Int {
        val band = when (level) {
            in 0..20 -> BatteryBand.LOW
            in 21..80 -> BatteryBand.MID
            else -> BatteryBand.FULL
        }
        if (!connected) return when (band) {
            BatteryBand.LOW -> R.drawable.ic_battery_low
            BatteryBand.MID -> R.drawable.ic_battery_mid
            BatteryBand.FULL -> R.drawable.ic_battery_full
        }
        return when (mode) {
            ChargingMode.NORMAL -> when (band) {
                BatteryBand.LOW -> R.drawable.ic_battery_low_charging
                BatteryBand.MID -> R.drawable.ic_battery_mid_charging
                BatteryBand.FULL -> R.drawable.ic_battery_full_charging
            }
            ChargingMode.BYPASS -> when (band) {
                BatteryBand.LOW -> R.drawable.ic_battery_low_pass_through_charging
                BatteryBand.MID -> R.drawable.ic_battery_mid_pass_through_charging
                BatteryBand.FULL -> R.drawable.ic_battery_full_pass_through_charging
            }
            ChargingMode.THRESHOLD -> when (band) {
                BatteryBand.LOW -> R.drawable.ic_battery_low_pass_through_charging_custom
                BatteryBand.MID -> R.drawable.ic_battery_mid_pass_through_charging_custom
                BatteryBand.FULL -> R.drawable.ic_battery_full_pass_through_charging_custom
            }
        }
    }

    private enum class BatteryBand { LOW, MID, FULL }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, ChargingQuickSettingsTile::class.java),
            )
        }
    }
}
