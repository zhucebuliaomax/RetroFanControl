package com.mmax.retrocontrol.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.edit
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.FanControlConfig
import com.mmax.retrocontrol.data.FanCurvePreferences
import com.mmax.retrocontrol.data.FanSelectionPreferences
import com.mmax.retrocontrol.data.FanSelectionSource
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.data.displayName
import com.mmax.retrocontrol.service.SystemControlService

/**
 * Standard third-party TileService supports one click target only:
 * tap toggles Off/on and long-press opens FanCurveTilePreferencesActivity.
 */
class FanQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile(currentConfig())
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val next = FanSelectionPreferences.toggle(prefs)
        updateTile(next)
        RootAccessManager.ensureRoot { granted ->
            val started = granted && runCatching {
                SystemControlService.startOrUpdate(applicationContext)
            }.isSuccess
            if (next.enabled && !started) {
                prefs.edit { putBoolean(Prefs.FAN_TILE_ENABLED, false) }
                FanSelectionPreferences.apply(prefs)
            }
            requestRefresh(applicationContext)
        }
    }

    private fun currentConfig(): FanControlConfig {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        return FanSelectionPreferences.apply(prefs, FanCurvePreferences.load(prefs))
    }

    private fun updateTile(config: FanControlConfig) {
        val selection = FanSelectionPreferences.load(
            getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
            config,
        )
        qsTile?.apply {
            icon = Icon.createWithResource(
                applicationContext,
                if (selection.enabled) R.drawable.ic_tile_fan_on else R.drawable.ic_tile_fan_off,
            )
            label = getString(R.string.tile_fan_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = when (val source = selection.source) {
                    FanSelectionSource.FollowPreset -> getString(R.string.follow_preset)
                    is FanSelectionSource.DirectCurve -> config.catalog
                        .profile(source.profileId)
                        ?.displayName(this@FanQuickSettingsTile)
                        ?: getString(R.string.follow_preset)
                }
            }
            state = if (selection.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(context, ComponentName(context, FanQuickSettingsTile::class.java))
        }
    }
}
