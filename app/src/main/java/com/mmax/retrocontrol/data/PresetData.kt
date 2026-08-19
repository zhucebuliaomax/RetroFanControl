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
            name = "Game",
            isDefault = true,
            fanCurveId = BuiltInFanCurve.NORMAL.id,
            buttonLayoutId = ButtonLayoutProfileCatalog.NINTENDO_ID,
        )

        fun otherAppsPreset(): ControlPreset = ControlPreset(
            id = OTHER_APPS_ID,
            name = "Other apps",
            isDefault = true,
            fanCurveId = BuiltInFanCurve.NORMAL.id,
            buttonLayoutId = ButtonLayoutProfileCatalog.NINTENDO_ID,
        )
    }
}

data class ControlPresetConfig(
    val catalog: ControlPresetCatalog = ControlPresetCatalog(),
    /** Default profile for games. Kept under the legacy property name for migration. */
    val selectedPresetId: String = ControlPresetCatalog.DEFAULT_ID,
    val selectedNonGamePresetId: String = ControlPresetCatalog.OTHER_APPS_ID,
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
