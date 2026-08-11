package com.mmax.retrocontrol.tile

import android.content.Context
import android.content.SharedPreferences
import com.mmax.retrocontrol.data.AppProfilePreferences
import com.mmax.retrocontrol.data.ButtonLayoutProfilePreferences
import com.mmax.retrocontrol.data.FanCurvePreferences
import com.mmax.retrocontrol.data.JoystickProfilePreferences
import com.mmax.retrocontrol.data.PerformanceProfilePreferences
import com.mmax.retrocontrol.data.PresetPreferences
import com.mmax.retrocontrol.data.Prefs
import com.mmax.retrocontrol.hardware.CpuFrequencyController

/** Saves a tile change to the current game, or to the shared preset for other apps. */
internal object CurrentAppControls {
    fun setFan(context: Context, prefs: SharedPreferences, profileId: String?) =
        withCatalogs(context, prefs) { packageName, catalogs ->
            if (AppProfilePreferences.isGame(context, packageName)) {
                AppProfilePreferences.setFanCurve(
                    prefs, packageName, profileId, catalogs.presetIds, catalogs.fanIds,
                    catalogs.joystickIds, catalogs.performanceIds, catalogs.buttonIds,
                )
            } else PresetPreferences.setFanCurve(
                prefs, catalogs.nonGamePresetId, profileId, catalogs.fanIds,
                catalogs.joystickIds, catalogs.performanceIds, catalogs.buttonIds,
            )
        }

    fun setJoystick(context: Context, prefs: SharedPreferences, profileId: String?) =
        withCatalogs(context, prefs) { packageName, catalogs ->
            if (AppProfilePreferences.isGame(context, packageName)) {
                AppProfilePreferences.setJoystickProfile(
                    prefs, packageName, profileId, catalogs.presetIds, catalogs.fanIds,
                    catalogs.joystickIds, catalogs.performanceIds, catalogs.buttonIds,
                )
            } else PresetPreferences.setJoystickProfile(
                prefs, catalogs.nonGamePresetId, profileId, catalogs.fanIds,
                catalogs.joystickIds, catalogs.performanceIds, catalogs.buttonIds,
            )
        }

    fun setButtonLayout(context: Context, prefs: SharedPreferences, profileId: String?) =
        withCatalogs(context, prefs) { packageName, catalogs ->
            if (AppProfilePreferences.isGame(context, packageName)) {
                AppProfilePreferences.setButtonLayout(
                    prefs, packageName, profileId, catalogs.presetIds, catalogs.fanIds,
                    catalogs.joystickIds, catalogs.performanceIds, catalogs.buttonIds,
                )
            } else PresetPreferences.setButtonLayout(
                prefs, catalogs.nonGamePresetId, profileId, catalogs.fanIds,
                catalogs.joystickIds, catalogs.performanceIds, catalogs.buttonIds,
            )
        }

    fun setPerformance(context: Context, prefs: SharedPreferences, profileId: String?) =
        withCatalogs(context, prefs) { packageName, catalogs ->
            if (AppProfilePreferences.isGame(context, packageName)) {
                AppProfilePreferences.setPerformanceProfile(
                    prefs, packageName, profileId, catalogs.presetIds, catalogs.fanIds,
                    catalogs.joystickIds, catalogs.performanceIds, catalogs.buttonIds,
                )
            } else PresetPreferences.setPerformanceProfile(
                prefs, catalogs.nonGamePresetId, profileId, catalogs.fanIds,
                catalogs.joystickIds, catalogs.performanceIds, catalogs.buttonIds,
            )
        }

    private inline fun withCatalogs(
        context: Context,
        prefs: SharedPreferences,
        block: (String, Catalogs) -> Unit,
    ) {
        val packageName = prefs.getString(Prefs.CURRENT_FOREGROUND_APP, null)
            ?.takeIf(String::isNotBlank) ?: return
        val fanIds = FanCurvePreferences.load(prefs).catalog.profiles
            .mapTo(mutableSetOf()) { it.id }
        val joystickIds = JoystickProfilePreferences.load(prefs).profiles
            .mapTo(mutableSetOf()) { it.id }
        val buttonIds = ButtonLayoutProfilePreferences.load(prefs).profiles
            .mapTo(mutableSetOf()) { it.id }
        val performanceIds = PerformanceProfilePreferences.load(
            prefs, CpuFrequencyController.detectPolicies(),
        ).profiles.mapTo(mutableSetOf()) { it.id }
        val presetConfig = PresetPreferences.load(
            prefs, fanIds, joystickIds, performanceIds, buttonIds,
        )
        val presetIds = presetConfig.catalog.presets.mapTo(mutableSetOf()) { it.id }
        block(
            packageName,
            Catalogs(
                presetIds, presetConfig.selectedNonGamePresetId, fanIds, joystickIds,
                performanceIds, buttonIds,
            ),
        )
    }

    private data class Catalogs(
        val presetIds: Set<String>,
        val nonGamePresetId: String,
        val fanIds: Set<String>,
        val joystickIds: Set<String>,
        val performanceIds: Set<String>,
        val buttonIds: Set<String>,
    )
}
