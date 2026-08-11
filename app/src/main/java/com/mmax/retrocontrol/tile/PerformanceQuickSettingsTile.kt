package com.mmax.retrocontrol.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.PerformanceProfileConfig
import com.mmax.retrocontrol.data.PerformanceProfilePreferences
import com.mmax.retrocontrol.data.PerformanceTilePreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.data.displayName
import com.mmax.retrocontrol.hardware.CpuFrequencyController
import com.mmax.retrocontrol.service.SystemControlService

/** Tap reapplies the selected profile; long-press opens the performance profile chooser. */
class PerformanceQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile(currentConfig())
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val config = currentConfig()
        val profileId = PerformanceTilePreferences.selectedProfileId(prefs, config)
            ?: prefs.getString(Prefs.LAST_APPLIED_PERFORMANCE_PROFILE, null)
                ?.takeIf { config.profile(it) != null }
            ?: config.stockProfile?.id
        if (profileId == null) {
            updateTile(config)
            return
        }

        PerformanceTilePreferences.select(prefs, profileId)
        CurrentAppControls.setPerformance(this, prefs, profileId)
        updateTile(config)
        RootAccessManager.ensureRoot { granted ->
            if (granted) {
                runCatching { SystemControlService.startOrUpdate(applicationContext) }
            }
            requestRefresh(applicationContext)
        }
    }

    private fun currentConfig(): PerformanceProfileConfig {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        return PerformanceProfilePreferences.load(prefs, CpuFrequencyController.detectPolicies())
    }

    private fun updateTile(config: PerformanceProfileConfig) {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val profile = PerformanceTilePreferences.selectedProfileId(prefs, config)
            ?.let(config::profile)
            ?: prefs.getString(Prefs.LAST_APPLIED_PERFORMANCE_PROFILE, null)
                ?.let(config::profile)
            ?: config.stockProfile
        qsTile?.apply {
            icon = Icon.createWithResource(
                applicationContext,
                R.drawable.ic_tile_performance,
            )
            label = getString(R.string.tile_performance_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = profile?.displayName(this@PerformanceQuickSettingsTile)
                    ?: getString(R.string.performance_policies_unavailable)
            }
            state = if (profile == null) Tile.STATE_UNAVAILABLE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, PerformanceQuickSettingsTile::class.java),
            )
        }
    }
}
