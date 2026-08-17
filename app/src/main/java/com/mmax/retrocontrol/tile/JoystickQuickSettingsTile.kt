package com.mmax.retrocontrol.tile

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.edit
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.AppProfilePreferences
import com.mmax.retrocontrol.data.JoystickProfilePreferences
import com.mmax.retrocontrol.data.JoystickSelectionPreferences
import com.mmax.retrocontrol.data.JoystickSelectionSource
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.service.SystemControlService

/** Tap toggles joystick lighting; long-press opens the profile chooser. */
class JoystickQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val next = JoystickSelectionPreferences.toggle(prefs)
        if (next.enabled) {
            val foreground = prefs.getString(Prefs.CURRENT_FOREGROUND_APP, null)
            val profileId = JoystickProfilePreferences.resolveEffectiveProfile(
                prefs = prefs,
                foregroundPackageName = foreground,
                foregroundIsGame = AppProfilePreferences.isGame(this, foreground),
            )?.id
            CurrentAppControls.setJoystick(this, prefs, profileId)
        }
        updateTile()
        RootAccessManager.ensureRoot { granted ->
            val started = granted && runCatching {
                SystemControlService.startOrUpdate(applicationContext)
            }.isSuccess
            if (next.enabled && !started) {
                prefs.edit { putBoolean(Prefs.JOYSTICK_TILE_ENABLED, false) }
            }
            requestRefresh(applicationContext)
        }
    }

    private fun updateTile() {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val catalog = JoystickProfilePreferences.load(prefs)
        val selection = JoystickSelectionPreferences.load(prefs, catalog)
        qsTile?.apply {
            icon = Icon.createWithResource(
                applicationContext,
                if (selection.enabled) {
                    R.drawable.ic_tile_joystick_on
                } else {
                    R.drawable.ic_tile_joystick_off
                },
            )
            label = getString(R.string.tile_joystick_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = when (val source = selection.source) {
                    JoystickSelectionSource.FollowProfile -> getString(R.string.follow_profile)
                    is JoystickSelectionSource.DirectProfile ->
                        catalog.profile(source.profileId)?.name ?: getString(R.string.follow_profile)
                }
            }
            state = if (selection.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, JoystickQuickSettingsTile::class.java),
            )
        }
    }
}
