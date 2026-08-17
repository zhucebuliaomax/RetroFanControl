package com.mmax.retrocontrol.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.AmbilightPreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.service.MediaProjectionActivity
import com.mmax.retrocontrol.service.SystemControlService

/** Tap toggles Ambilight; long-press adjusts brightness. */
class AmbilightQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val enabled = !AmbilightPreferences.isEnabled(prefs)
        AmbilightPreferences.setEnabled(prefs, enabled)
        updateTile()

        if (enabled) {
            val captureIntent = MediaProjectionActivity.createIntent(this)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        CAPTURE_REQUEST,
                        captureIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(captureIntent)
            }
        }

        RootAccessManager.ensureRoot { granted ->
            val started = granted && runCatching {
                SystemControlService.startOrUpdate(applicationContext)
            }.isSuccess
            if (enabled && !started) AmbilightPreferences.setEnabled(prefs, false)
            requestRefresh(applicationContext)
        }
    }

    private fun updateTile() {
        val enabled = AmbilightPreferences.isEnabled(
            getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        )
        qsTile?.apply {
            icon = Icon.createWithResource(
                applicationContext,
                if (enabled) R.drawable.ic_tile_ambilight_on
                else R.drawable.ic_tile_ambilight_off,
            )
            label = getString(R.string.tile_ambilight_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(if (enabled) R.string.tile_on else R.string.tile_off)
            }
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    companion object {
        private const val CAPTURE_REQUEST = 43

        fun requestRefresh(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, AmbilightQuickSettingsTile::class.java),
            )
        }
    }
}
