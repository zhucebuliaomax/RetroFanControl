package com.mmax.retrocontrol.data

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object PresetPreferences {
    fun load(
        prefs: SharedPreferences,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPresetConfig {
        val stored = prefs.getString(Prefs.PRESET_CATALOG, null)
        val decoded = stored?.let(::decodeCatalog) ?: ControlPresetCatalog()
        val normalizedPresets = decoded.presets
            .ifEmpty { ControlPresetCatalog().presets }
            .let { presets ->
                if (presets.any(ControlPreset::isDefault)) presets
                else listOf(ControlPresetCatalog.defaultPreset()) + presets
            }
            .map { preset ->
                normalizeControlReferences(
                    preset,
                    availableFanCurveIds,
                    availableJoystickProfileIds,
                    availablePerformanceProfileIds,
                    availableButtonLayoutProfileIds,
                )
            }
        val catalog = ControlPresetCatalog(normalizedPresets)
        val legacySelectedId = prefs.getString(Prefs.SELECTED_PRESET, null)
        val selectedId = prefs.getString(Prefs.SELECTED_GAME_PROFILE, legacySelectedId)
            ?.takeIf { catalog.preset(it) != null }
            ?: catalog.presets.first { it.isDefault }.id
        val selectedNonGameId = prefs.getString(
            Prefs.SELECTED_NON_GAME_PROFILE,
            legacySelectedId,
        )?.takeIf { catalog.preset(it) != null }
            ?: catalog.presets.first { it.isDefault }.id
        val config = ControlPresetConfig(catalog, selectedId, selectedNonGameId)

        if (
            stored == null || decoded != catalog ||
            prefs.getString(Prefs.SELECTED_GAME_PROFILE, null) != selectedId ||
            prefs.getString(Prefs.SELECTED_NON_GAME_PROFILE, null) != selectedNonGameId
        ) {
            persist(prefs, config)
        }
        return config
    }

    internal fun normalizeControlReferences(
        preset: ControlPreset,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>?,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPreset = preset.copy(
        fanCurveId = preset.fanCurveId?.takeIf(availableFanCurveIds::contains),
        joystickId = preset.joystickId?.takeIf(availableJoystickProfileIds::contains),
        buttonLayoutId = if (availableButtonLayoutProfileIds == null) {
            preset.buttonLayoutId
        } else {
            preset.buttonLayoutId?.takeIf(availableButtonLayoutProfileIds::contains)
        },
        performanceProfileId = when {
            availablePerformanceProfileIds == null -> preset.performanceProfileId
            preset.performanceProfileId == null &&
                BuiltInPerformanceProfile.STOCK.id in availablePerformanceProfileIds -> {
                BuiltInPerformanceProfile.STOCK.id
            }
            preset.performanceProfileId in availablePerformanceProfileIds -> {
                preset.performanceProfileId
            }
            BuiltInPerformanceProfile.STOCK.id in availablePerformanceProfileIds -> {
                BuiltInPerformanceProfile.STOCK.id
            }
            else -> null
        },
    )

    fun select(
        prefs: SharedPreferences,
        presetId: String,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPresetConfig {
        val current = load(
            prefs,
            availableFanCurveIds,
            availableJoystickProfileIds,
            availablePerformanceProfileIds,
            availableButtonLayoutProfileIds,
        )
        val selectedId = presetId.takeIf { current.catalog.preset(it) != null }
            ?: current.selectedPresetId
        return current.copy(selectedPresetId = selectedId).also { persist(prefs, it) }
    }

    fun selectDefault(
        prefs: SharedPreferences,
        presetId: String,
        isGame: Boolean,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPresetConfig {
        val current = load(
            prefs,
            availableFanCurveIds,
            availableJoystickProfileIds,
            availablePerformanceProfileIds,
            availableButtonLayoutProfileIds,
        )
        val selectedId = presetId.takeIf { current.catalog.preset(it) != null }
            ?: return current
        return if (isGame) {
            current.copy(selectedPresetId = selectedId)
        } else {
            current.copy(selectedNonGamePresetId = selectedId)
        }.also { persist(prefs, it) }
    }

    fun add(
        prefs: SharedPreferences,
        name: String,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): Pair<ControlPresetConfig, String> {
        val current = load(
            prefs,
            availableFanCurveIds,
            availableJoystickProfileIds,
            availablePerformanceProfileIds,
            availableButtonLayoutProfileIds,
        )
        val template = current.selectedPreset
        val id = "preset-${UUID.randomUUID()}"
        val preset = template.copy(
            id = id,
            name = name.trim().take(40).ifBlank { "New preset" },
            isDefault = false,
        )
        val updated = current.copy(catalog = current.catalog.plus(preset))
        persist(prefs, updated)
        return updated to id
    }

    fun addImported(
        prefs: SharedPreferences,
        preset: ControlPreset,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPresetConfig {
        val current = load(
            prefs,
            availableFanCurveIds,
            availableJoystickProfileIds,
            availablePerformanceProfileIds,
            availableButtonLayoutProfileIds,
        )
        val imported = normalizeControlReferences(
            preset.copy(
                id = "preset-${UUID.randomUUID()}",
                name = preset.name.trim().take(40).ifBlank { "New preset" },
                isDefault = false,
            ),
            availableFanCurveIds,
            availableJoystickProfileIds,
            availablePerformanceProfileIds,
            availableButtonLayoutProfileIds,
        )
        return current.copy(catalog = current.catalog.plus(imported)).also {
            persist(prefs, it)
        }
    }

    fun rename(
        prefs: SharedPreferences,
        presetId: String,
        name: String,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPresetConfig = update(
        prefs, presetId, availableFanCurveIds, availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) {
        it.renamed(name)
    }

    fun setFanCurve(
        prefs: SharedPreferences,
        presetId: String,
        fanCurveId: String?,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPresetConfig = update(
        prefs, presetId, availableFanCurveIds, availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) {
        it.copy(fanCurveId = fanCurveId?.takeIf(availableFanCurveIds::contains))
    }

    fun setJoystickProfile(
        prefs: SharedPreferences,
        presetId: String,
        joystickProfileId: String?,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPresetConfig = update(
        prefs, presetId, availableFanCurveIds, availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) {
        it.copy(
            joystickId = joystickProfileId?.takeIf(availableJoystickProfileIds::contains)
        )
    }

    fun setButtonLayout(
        prefs: SharedPreferences,
        presetId: String,
        buttonLayoutProfileId: String?,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>,
    ): ControlPresetConfig = update(
        prefs,
        presetId,
        availableFanCurveIds,
        availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) {
        it.copy(
            buttonLayoutId = buttonLayoutProfileId
                ?.takeIf(availableButtonLayoutProfileIds::contains),
        )
    }

    fun setPerformanceProfile(
        prefs: SharedPreferences,
        presetId: String,
        performanceProfileId: String?,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPresetConfig = update(
        prefs,
        presetId,
        availableFanCurveIds,
        availableJoystickProfileIds,
        availablePerformanceProfileIds,
        availableButtonLayoutProfileIds,
    ) {
        it.copy(
            performanceProfileId = performanceProfileId
                ?.takeIf(availablePerformanceProfileIds::contains),
        )
    }

    fun delete(
        prefs: SharedPreferences,
        presetId: String,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>? = null,
        availableButtonLayoutProfileIds: Set<String>? = null,
    ): ControlPresetConfig {
        val current = load(
            prefs,
            availableFanCurveIds,
            availableJoystickProfileIds,
            availablePerformanceProfileIds,
            availableButtonLayoutProfileIds,
        )
        val target = current.catalog.preset(presetId) ?: return current
        if (target.isDefault) return current
        val catalog = current.catalog.remove(presetId)
        val selectedId = current.selectedPresetId.takeIf { catalog.preset(it) != null }
            ?: catalog.presets.first { it.isDefault }.id
        val selectedNonGameId = current.selectedNonGamePresetId
            .takeIf { catalog.preset(it) != null }
            ?: catalog.presets.first { it.isDefault }.id
        return ControlPresetConfig(catalog, selectedId, selectedNonGameId)
            .also { persist(prefs, it) }
    }

    private fun update(
        prefs: SharedPreferences,
        presetId: String,
        availableFanCurveIds: Set<String>,
        availableJoystickProfileIds: Set<String>,
        availablePerformanceProfileIds: Set<String>?,
        availableButtonLayoutProfileIds: Set<String>?,
        transform: (ControlPreset) -> ControlPreset,
    ): ControlPresetConfig {
        val current = load(
            prefs,
            availableFanCurveIds,
            availableJoystickProfileIds,
            availablePerformanceProfileIds,
            availableButtonLayoutProfileIds,
        )
        val target = current.catalog.preset(presetId) ?: return current
        val updated = current.copy(catalog = current.catalog.replace(transform(target)))
        persist(prefs, updated)
        return updated
    }

    private fun persist(prefs: SharedPreferences, config: ControlPresetConfig) {
        prefs.edit {
            putString(Prefs.PRESET_CATALOG, encodeCatalog(config.catalog))
            putString(Prefs.SELECTED_PRESET, config.selectedPresetId)
            putString(Prefs.SELECTED_GAME_PROFILE, config.selectedPresetId)
            putString(Prefs.SELECTED_NON_GAME_PROFILE, config.selectedNonGamePresetId)
        }
    }

    private fun encodeCatalog(catalog: ControlPresetCatalog): String {
        val presets = JSONArray()
        catalog.presets.forEach { preset ->
            presets.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("default", preset.isDefault)
                    .put("fanCurveId", preset.fanCurveId ?: JSONObject.NULL)
                    .put("joystickId", preset.joystickId ?: JSONObject.NULL)
                    .put("buttonLayoutId", preset.buttonLayoutId ?: JSONObject.NULL)
                    .put("performanceProfileId", preset.performanceProfileId ?: JSONObject.NULL)
            )
        }
        return JSONObject().put("presets", presets).toString()
    }

    private fun decodeCatalog(serialized: String): ControlPresetCatalog = runCatching {
        val array = JSONObject(serialized).getJSONArray("presets")
        val presets = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.getString("id").takeIf(String::isNotBlank) ?: continue
                val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
                add(
                    ControlPreset(
                        id = id,
                        name = name,
                        isDefault = item.optBoolean("default", false),
                        fanCurveId = item.nullableString("fanCurveId"),
                        joystickId = item.nullableString("joystickId"),
                        buttonLayoutId = item.nullableString("buttonLayoutId"),
                        performanceProfileId = item.nullableString("performanceProfileId"),
                    )
                )
            }
        }
        ControlPresetCatalog(presets)
    }.getOrElse { ControlPresetCatalog() }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}

object FanSelectionPreferences {
    private const val SOURCE_PRESET = "PRESET"
    private const val SOURCE_CURVE = "CURVE"

    fun load(prefs: SharedPreferences, fanConfig: FanControlConfig): FanSelectionConfig {
        val storedSource = prefs.getString(Prefs.FAN_SELECTION_SOURCE, null)
        val source = when (storedSource) {
            SOURCE_PRESET -> FanSelectionSource.FollowPreset
            SOURCE_CURVE -> prefs.getString(Prefs.FAN_SELECTION_CURVE, null)
                ?.takeIf { fanConfig.catalog.profile(it) != null }
                ?.let(FanSelectionSource::DirectCurve)
                ?: FanSelectionSource.FollowPreset
            else -> fanConfig.activeProfileId
                ?.let(FanSelectionSource::DirectCurve)
                ?: FanSelectionSource.FollowPreset
        }
        val enabled = if (prefs.contains(Prefs.FAN_TILE_ENABLED)) {
            prefs.getBoolean(Prefs.FAN_TILE_ENABLED, true)
        } else {
            fanConfig.enabled
        }
        val config = FanSelectionConfig(source, enabled)
        if (storedSource == null || (storedSource == SOURCE_CURVE && source is FanSelectionSource.FollowPreset)) {
            persist(prefs, config)
        }
        return config
    }

    fun selectFollowPreset(prefs: SharedPreferences): FanControlConfig {
        val current = FanCurvePreferences.load(prefs)
        persist(prefs, FanSelectionConfig(FanSelectionSource.FollowPreset, enabled = true))
        return apply(prefs, current)
    }

    fun selectDirectCurve(prefs: SharedPreferences, profileId: String): FanControlConfig {
        val current = FanCurvePreferences.load(prefs)
        if (current.catalog.profile(profileId) == null) return current
        persist(
            prefs,
            FanSelectionConfig(FanSelectionSource.DirectCurve(profileId), enabled = true),
        )
        return apply(prefs, current)
    }

    fun toggle(prefs: SharedPreferences): FanControlConfig {
        val current = FanCurvePreferences.load(prefs)
        val selection = load(prefs, current)
        persist(prefs, selection.copy(enabled = !selection.enabled))
        return apply(prefs, current)
    }

    fun apply(
        prefs: SharedPreferences,
        suppliedFanConfig: FanControlConfig? = null,
        foregroundPackageName: String? = null,
    ): FanControlConfig {
        val resolved = resolveEffectiveConfig(
            prefs = prefs,
            suppliedFanConfig = suppliedFanConfig,
            foregroundPackageName = foregroundPackageName,
        )
        val current = suppliedFanConfig ?: FanCurvePreferences.load(prefs)
        return if (current.activeProfileId == resolved.activeProfileId) current
        else FanCurvePreferences.select(prefs, resolved.activeProfileId)
    }

    /**
     * Makes a foreground-app change the latest fan-selection event. The resolved app/default
     * curve is copied into the tile selection so the tile reflects what is actually active;
     * a later tile action can still override it until the foreground app changes again.
     */
    fun syncToForegroundApp(
        prefs: SharedPreferences,
        suppliedFanConfig: FanControlConfig? = null,
        foregroundPackageName: String,
        foregroundIsGame: Boolean = false,
    ): FanControlConfig {
        val current = suppliedFanConfig ?: FanCurvePreferences.load(prefs)
        val presetConfig = loadPresetConfig(prefs, current)
        val appProfile = loadAppProfiles(prefs, current, presetConfig)[foregroundPackageName]
        val targetId = resolveAppTargetProfileId(
            presetConfig = presetConfig,
            fanCatalog = current.catalog,
            appProfile = appProfile,
            appIsGame = foregroundIsGame,
        )
        val previousSelection = load(prefs, current)
        persist(
            prefs,
            selectionForAppTarget(
                targetId = targetId,
                previousSelection = previousSelection,
                fallbackProfileId = current.activeProfileId
                    ?: prefs.getString(Prefs.LAST_FAN_CURVE, null),
                fanCatalog = current.catalog,
            ),
        )
        return if (current.activeProfileId == targetId) current
        else FanCurvePreferences.select(prefs, targetId)
    }

    /** Resolves the current tile selection without persisting transient app state. */
    fun resolveEffectiveConfig(
        prefs: SharedPreferences,
        suppliedFanConfig: FanControlConfig? = null,
        foregroundPackageName: String? = null,
        foregroundIsGame: Boolean = false,
    ): FanControlConfig {
        val current = suppliedFanConfig ?: FanCurvePreferences.load(prefs)
        var selection = load(prefs, current)
        if (
            selection.source is FanSelectionSource.DirectCurve &&
            current.catalog.profile(selection.source.profileId) == null
        ) {
            selection = selection.copy(source = FanSelectionSource.FollowPreset)
            persist(prefs, selection)
        }
        val presetConfig = loadPresetConfig(prefs, current)
        val appProfiles = loadAppProfiles(prefs, current, presetConfig)
        val appProfile = foregroundPackageName?.let(appProfiles::get)
        val targetId = resolveTargetProfileId(
            selection = selection,
            presetConfig = presetConfig,
            fanCatalog = current.catalog,
            appProfile = appProfile,
            appIsGame = foregroundIsGame,
        )
        return current.copy(activeProfileId = targetId)
    }

    private fun loadPresetConfig(
        prefs: SharedPreferences,
        fanConfig: FanControlConfig,
    ): ControlPresetConfig = PresetPreferences.load(
        prefs,
        fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id },
        JoystickProfilePreferences.load(prefs).profiles
            .mapTo(mutableSetOf()) { it.id },
    )

    private fun loadAppProfiles(
        prefs: SharedPreferences,
        fanConfig: FanControlConfig,
        presetConfig: ControlPresetConfig,
    ): Map<String, AppControlProfile> = AppProfilePreferences.load(
        prefs = prefs,
        availablePresetIds = presetConfig.catalog.presets.mapTo(mutableSetOf()) { it.id },
        availableFanCurveIds = fanConfig.catalog.profiles.mapTo(mutableSetOf()) { it.id },
        availableJoystickProfileIds = JoystickProfilePreferences.load(prefs).profiles
            .mapTo(mutableSetOf()) { it.id },
    )

    internal fun resolveTargetProfileId(
        selection: FanSelectionConfig,
        presetConfig: ControlPresetConfig,
        fanCatalog: FanCurveCatalog,
        appProfile: AppControlProfile? = null,
        appIsGame: Boolean = false,
    ): String? {
        if (!selection.enabled) return null
        return when (val source = selection.source) {
            FanSelectionSource.FollowPreset -> resolveAppTargetProfileId(
                presetConfig = presetConfig,
                fanCatalog = fanCatalog,
                appProfile = appProfile,
                appIsGame = appIsGame,
            )
            is FanSelectionSource.DirectCurve -> source.profileId
                .takeIf { fanCatalog.profile(it) != null }
        }
    }

    internal fun resolveAppTargetProfileId(
        presetConfig: ControlPresetConfig,
        fanCatalog: FanCurveCatalog,
        appProfile: AppControlProfile? = null,
        appIsGame: Boolean = false,
    ): String? = appProfile?.fanCurveId
        ?.takeIf { fanCatalog.profile(it) != null }
        ?: AppProfilePreferences.effectivePreset(
            appProfile, presetConfig, appIsGame,
        ).fanCurveId
            ?.takeIf { fanCatalog.profile(it) != null }

    internal fun selectionForAppTarget(
        targetId: String?,
        previousSelection: FanSelectionConfig,
        fallbackProfileId: String?,
        fanCatalog: FanCurveCatalog,
    ): FanSelectionConfig {
        if (targetId != null) {
            return FanSelectionConfig(
                source = FanSelectionSource.DirectCurve(targetId),
                enabled = true,
            )
        }
        val rememberedSource = when (val source = previousSelection.source) {
            is FanSelectionSource.DirectCurve -> source
                .takeIf { fanCatalog.profile(it.profileId) != null }
            FanSelectionSource.FollowPreset -> fallbackProfileId
                ?.takeIf { fanCatalog.profile(it) != null }
                ?.let(FanSelectionSource::DirectCurve)
        } ?: FanSelectionSource.FollowPreset
        return FanSelectionConfig(source = rememberedSource, enabled = false)
    }

    private fun persist(prefs: SharedPreferences, config: FanSelectionConfig) {
        prefs.edit {
            putString(
                Prefs.FAN_SELECTION_SOURCE,
                if (config.source is FanSelectionSource.FollowPreset) SOURCE_PRESET else SOURCE_CURVE,
            )
            putString(
                Prefs.FAN_SELECTION_CURVE,
                (config.source as? FanSelectionSource.DirectCurve)?.profileId,
            )
            putBoolean(Prefs.FAN_TILE_ENABLED, config.enabled)
        }
    }
}
