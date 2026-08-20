package com.mmax.retrocontrol.data

import android.content.SharedPreferences
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class AppControlProfile(
    val packageName: String,
    /** Null follows the currently selected default preset. */
    val presetId: String? = null,
    /** Null leaves this control to the selected preset. */
    val fanCurveId: String? = null,
    val joystickId: String? = null,
    val buttonLayoutId: String? = null,
    val performanceProfileId: String? = null,
)

object AppProfilePreferences {
    fun isGame(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).category ==
                ApplicationInfo.CATEGORY_GAME
        }.getOrDefault(false)
    }
    fun load(
        prefs: SharedPreferences,
        availablePresetIds: Set<String>,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): Map<String, AppControlProfile> {
        val stored = prefs.getString(Prefs.APP_PROFILE_CATALOG, null) ?: return emptyMap()
        val decoded = decode(stored)
        val normalized = decoded.mapValues { (_, profile) ->
            profile.copy(
                presetId = profile.presetId?.takeIf(availablePresetIds::contains),
                fanCurveId = profile.fanCurveId?.takeIf(availableFanCurveIds::contains),
                joystickId = profile.joystickId
                    ?.takeIf(availableJoystickProfileIds::contains),
                buttonLayoutId = if (availableButtonLayoutProfileIds == null) {
                    profile.buttonLayoutId
                } else {
                    profile.buttonLayoutId?.takeIf(availableButtonLayoutProfileIds::contains)
                },
                performanceProfileId = if (availablePerformanceProfileIds == null) {
                    profile.performanceProfileId
                } else {
                    profile.performanceProfileId
                        ?.takeIf(availablePerformanceProfileIds::contains)
                },
            )
        }
        if (decoded != normalized) persist(prefs, normalized)
        return normalized
    }

    fun setPreset(
        prefs: SharedPreferences,
        packageName: String,
        presetId: String?,
        availablePresetIds: Set<String>,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): Map<String, AppControlProfile> = update(
        prefs,
        packageName,
        availablePresetIds,
        availableFanCurveIds,
        availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) { profile ->
        profile.copy(presetId = presetId?.takeIf(availablePresetIds::contains))
    }

    fun setFanCurve(
        prefs: SharedPreferences,
        packageName: String,
        fanCurveId: String?,
        availablePresetIds: Set<String>,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): Map<String, AppControlProfile> = update(
        prefs,
        packageName,
        availablePresetIds,
        availableFanCurveIds,
        availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) { profile ->
        profile.copy(fanCurveId = fanCurveId?.takeIf(availableFanCurveIds::contains))
    }

    fun setJoystickProfile(
        prefs: SharedPreferences,
        packageName: String,
        joystickProfileId: String?,
        availablePresetIds: Set<String>,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): Map<String, AppControlProfile> = update(
        prefs,
        packageName,
        availablePresetIds,
        availableFanCurveIds,
        availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) { profile ->
        profile.copy(
            joystickId = joystickProfileId?.takeIf(availableJoystickProfileIds::contains)
        )
    }

    fun setButtonLayout(
        prefs: SharedPreferences,
        packageName: String,
        buttonLayoutProfileId: String?,
        availablePresetIds: Set<String>,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>,
    ): Map<String, AppControlProfile> = update(
        prefs,
        packageName,
        availablePresetIds,
        availableFanCurveIds,
        availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) { profile ->
        profile.copy(
            buttonLayoutId = buttonLayoutProfileId
                ?.takeIf(availableButtonLayoutProfileIds::contains),
        )
    }

    fun setPerformanceProfile(
        prefs: SharedPreferences,
        packageName: String,
        performanceProfileId: String?,
        availablePresetIds: Set<String>,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): Map<String, AppControlProfile> = update(
        prefs,
        packageName,
        availablePresetIds,
        availableFanCurveIds,
        availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) { profile ->
        profile.copy(
            performanceProfileId = performanceProfileId
                ?.takeIf(availablePerformanceProfileIds::contains),
        )
    }

    fun effectivePreset(
        profile: AppControlProfile?,
        presetConfig: ControlPresetConfig,
        isGame: Boolean = true,
    ): ControlPreset = presetConfig.catalog.preset(profile?.presetId)
        ?: presetConfig.defaultPreset(isGame)

    private fun update(
        prefs: SharedPreferences,
        packageName: String,
        availablePresetIds: Set<String>,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>?,
        availableButtonLayoutProfileIds: Set<String>?,
        transform: (AppControlProfile) -> AppControlProfile,
    ): Map<String, AppControlProfile> {
        if (packageName.isBlank()) {
            return load(
                prefs,
                availablePresetIds,
                availableFanCurveIds,
                availableJoystickProfileIds,
                availablePerformanceProfileIds,
                availableButtonLayoutProfileIds,
            )
        }
        val current = load(
            prefs,
            availablePresetIds,
            availableFanCurveIds,
            availableJoystickProfileIds,
            availablePerformanceProfileIds,
            availableButtonLayoutProfileIds,
        )
        val updatedProfile = transform(
            current[packageName] ?: AppControlProfile(packageName = packageName)
        )
        val updated = current + (packageName to updatedProfile)
        persist(prefs, updated)
        return updated
    }

    private fun persist(
        prefs: SharedPreferences,
        profiles: Map<String, AppControlProfile>,
    ) {
        val entries = JSONArray()
        profiles.values.sortedBy(AppControlProfile::packageName).forEach { profile ->
            entries.put(
                JSONObject()
                    .put("packageName", profile.packageName)
                    .put("presetId", profile.presetId ?: JSONObject.NULL)
                    .put("fanCurveId", profile.fanCurveId ?: JSONObject.NULL)
                    .put("joystickId", profile.joystickId ?: JSONObject.NULL)
                    .put("buttonLayoutId", profile.buttonLayoutId ?: JSONObject.NULL)
                    .put("performanceProfileId", profile.performanceProfileId ?: JSONObject.NULL)
            )
        }
        prefs.edit {
            putString(Prefs.APP_PROFILE_CATALOG, JSONObject().put("profiles", entries).toString())
        }
    }

    private fun decode(value: String): Map<String, AppControlProfile> = runCatching {
        val entries = JSONObject(value).getJSONArray("profiles")
        buildMap {
            repeat(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                val packageName = item.optString("packageName").takeIf(String::isNotBlank)
                    ?: return@repeat
                put(
                    packageName,
                    AppControlProfile(
                        packageName = packageName,
                        presetId = item.nullableString("presetId"),
                        fanCurveId = item.nullableString("fanCurveId"),
                        joystickId = item.nullableString("joystickId"),
                        buttonLayoutId = item.nullableString("buttonLayoutId"),
                        performanceProfileId = item.nullableString("performanceProfileId"),
                    )
                )
            }
        }
    }.getOrElse { emptyMap() }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}
