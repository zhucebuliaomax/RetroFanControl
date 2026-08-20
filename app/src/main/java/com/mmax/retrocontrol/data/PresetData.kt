package com.mmax.retrocontrol.data

data class ControlPreset(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    /** Null represents Off. */
    val fanCurveId: String? = BuiltInFanCurve.NORMAL.id,
    val joystickId: String? = null,
    val buttonLayoutId: String? = ButtonLayoutProfileCatalog.NINTENDO_ID,
    val performanceProfileId: String? = BuiltInPerformanceProfile.STOCK.id,
) {
    init {
        require(id.isNotBlank()) { "A preset requires an id" }
        require(name.isNotBlank()) { "A preset requires a name" }
    }

    fun renamed(value: String): ControlPreset =
        copy(name = value.trim().take(40).ifBlank { name })
}

data class ControlPresetCatalog(
    val presets: List<ControlPreset> = factoryPresets(),
) {
    fun preset(id: String?): ControlPreset? = presets.firstOrNull { it.id == id }

    fun replace(preset: ControlPreset): ControlPresetCatalog = copy(
        presets = presets.map { existing ->
            if (existing.id == preset.id) preset else existing
        }
    )

    fun plus(preset: ControlPreset): ControlPresetCatalog =
        copy(presets = presets.filterNot { it.id == preset.id } + preset)

    fun remove(id: String): ControlPresetCatalog =
        copy(presets = presets.filterNot { it.id == id })

    companion object {
        const val DEFAULT_ID = "default"
        const val OTHER_APPS_ID = "other-apps"

        val factoryIds: Set<String> = setOf(DEFAULT_ID, OTHER_APPS_ID)

        fun factoryPresets(): List<ControlPreset> = listOf(
            defaultPreset(),
            otherAppsPreset(),
        )

        fun defaultPreset(): ControlPreset = ControlPreset(
            id = DEFAULT_ID,
            name = requireNotNull(BundledDefaultConfig.itemString("profile", DEFAULT_ID, "name")),
            isDefault = true,
            fanCurveId = BundledDefaultConfig.itemString("profile", DEFAULT_ID, "fanCurveId"),
            joystickId = BundledDefaultConfig.itemString("profile", DEFAULT_ID, "joystickId"),
            buttonLayoutId = BundledDefaultConfig.itemString(
                "profile", DEFAULT_ID, "buttonLayoutId"
            ),
            performanceProfileId = BundledDefaultConfig.itemString(
                "profile", DEFAULT_ID, "performanceProfileId"
            ),
        )

        fun otherAppsPreset(): ControlPreset = ControlPreset(
            id = OTHER_APPS_ID,
            name = requireNotNull(
                BundledDefaultConfig.itemString("profile", OTHER_APPS_ID, "name")
            ),
            isDefault = true,
            fanCurveId = BundledDefaultConfig.itemString(
                "profile", OTHER_APPS_ID, "fanCurveId"
            ),
            joystickId = BundledDefaultConfig.itemString(
                "profile", OTHER_APPS_ID, "joystickId"
            ),
            buttonLayoutId = BundledDefaultConfig.itemString(
                "profile", OTHER_APPS_ID, "buttonLayoutId"
            ),
            performanceProfileId = BundledDefaultConfig.itemString(
                "profile", OTHER_APPS_ID, "performanceProfileId"
            ),
        )
    }
}

data class ControlPresetConfig(
    val catalog: ControlPresetCatalog = ControlPresetCatalog(),
    /** Default profile for games. Kept under the legacy property name for migration. */
    val selectedPresetId: String = BundledDefaultConfig.settingString("gamePresetId"),
    val selectedNonGamePresetId: String = BundledDefaultConfig.settingString("nonGamePresetId"),
) {
    val selectedPreset: ControlPreset
        get() = catalog.preset(selectedPresetId)
            ?: ControlPresetCatalog.defaultPreset()

    fun defaultPreset(isGame: Boolean): ControlPreset = catalog.preset(
        if (isGame) selectedPresetId else selectedNonGamePresetId
    ) ?: ControlPresetCatalog.defaultPreset()
}

sealed interface FanSelectionSource {
    data object FollowPreset : FanSelectionSource
    data class DirectCurve(val profileId: String) : FanSelectionSource
}

data class FanSelectionConfig(
    val source: FanSelectionSource,
    val enabled: Boolean,
)
