package com.mmax.retrocontrol.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mmax.retrocontrol.R
import com.mmax.retrocontrol.RootAccessManager
import com.mmax.retrocontrol.data.ButtonLayoutProfileCatalog
import com.mmax.retrocontrol.data.ButtonLayoutProfilePreferences
import com.mmax.retrocontrol.data.ButtonLayoutTilePreferences
import com.mmax.retrocontrol.data.FaceButtonLayout
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.hardware.GamepadController
import com.mmax.retrocontrol.service.SystemControlService

/** Tap cycles every button-layout profile; long-press opens the profile chooser. */
class ButtonLayoutQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile(currentCatalog())
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val catalog = ButtonLayoutProfilePreferences.load(prefs)
        val selected = ButtonLayoutTilePreferences.selectNext(prefs, catalog)
        selected?.let { CurrentAppControls.setButtonLayout(this, prefs, it.id) }
        updateTile(catalog)
        if (selected == null) return

        RootAccessManager.ensureRoot { granted ->
            if (granted) {
                runCatching { SystemControlService.startOrUpdate(applicationContext) }
            }
            requestRefresh(applicationContext)
        }
    }

    private fun currentCatalog(): ButtonLayoutProfileCatalog =
        ButtonLayoutProfilePreferences.load(
            getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE),
        )

    private fun updateTile(catalog: ButtonLayoutProfileCatalog) {
        val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        val selected = ButtonLayoutTilePreferences.selectedProfileId(prefs, catalog)
            ?.let(catalog::profile)
        val layout = selected?.layout
            ?: GamepadController.readState().getOrNull()?.layout
            ?: FaceButtonLayout.NINTENDO
        qsTile?.apply {
            icon = Icon.createWithResource(
                applicationContext,
                if (layout == FaceButtonLayout.XBOX) {
                    R.drawable.ic_tile_button_layout_xbox
                } else {
                    R.drawable.ic_tile_button_layout_nintendo
                },
            )
            label = getString(R.string.tile_button_layout_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = selected?.name ?: getString(R.string.follow_system)
            }
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, ButtonLayoutQuickSettingsTile::class.java),
            )
        }
    }
}
