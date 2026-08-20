package com.mmax.retrocontrol.data

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class FaceButtonLayout(val sysfsValue: String) {
    NINTENDO("nintendo"),
    XBOX("xbox"),
}

enum class GamepadButtonMapping(val sysfsValue: String) {
    NONE("none"),
    HOME("home"),
    SELECT("select"),
    START("start"),
    BACK("back"),
    A("a"),
    B("b"),
    X("x"),
    Y("y"),
    L1("l1"),
    L2("l2"),
    L3("l3"),
    R1("r1"),
    R2("r2"),
    R3("r3"),
    DOWN("down"),
    UP("up"),
    LEFT("left"),
    RIGHT("right"),
}

enum class GamepadTriggerMode(val sysfsValue: String) {
    BOTH("both"),
    ANALOG("analog"),
    DIGITAL("digital"),
}

data class ButtonLayoutProfile(
    val id: String,
    val name: String,
    val layout: FaceButtonLayout = FaceButtonLayout.NINTENDO,
    val m1: GamepadButtonMapping = GamepadButtonMapping.NONE,
    val m2: GamepadButtonMapping = GamepadButtonMapping.NONE,
    val triggerMode: GamepadTriggerMode = GamepadTriggerMode.BOTH,
) {
    init {
        require(id.isNotBlank()) { "A button layout profile requires an id" }
        require(name.isNotBlank()) { "A button layout profile requires a name" }
    }

    fun normalized(): ButtonLayoutProfile {
        val normalized = copy(name = name.trim().take(40).ifBlank { "Button layout" })
        return when (id) {
            ButtonLayoutProfileCatalog.XBOX_ID -> normalized.copy(
                name = "Xbox",
                layout = FaceButtonLayout.XBOX,
            )
            ButtonLayoutProfileCatalog.NINTENDO_ID -> normalized.copy(
                name = "Nintendo",
                layout = FaceButtonLayout.NINTENDO,
            )
            else -> normalized
        }
    }

    fun renamed(value: String): ButtonLayoutProfile =
        copy(name = value.trim().take(40).ifBlank { name })

    val isBuiltIn: Boolean
        get() = id == ButtonLayoutProfileCatalog.XBOX_ID ||
            id == ButtonLayoutProfileCatalog.NINTENDO_ID

}

data class ButtonLayoutProfileCatalog(
    val profiles: List<ButtonLayoutProfile> = factoryProfiles(),
) {
    fun profile(id: String?): ButtonLayoutProfile? = profiles.firstOrNull { it.id == id }

    fun replace(profile: ButtonLayoutProfile): ButtonLayoutProfileCatalog = copy(
        profiles = profiles.map { current ->
            if (current.id == profile.id) profile.normalized() else current
        },
    )

    fun plus(profile: ButtonLayoutProfile): ButtonLayoutProfileCatalog = copy(
        profiles = profiles.filterNot { it.id == profile.id } + profile.normalized(),
    )

    fun remove(id: String): ButtonLayoutProfileCatalog = if (id in factoryIds) {
        this
    } else {
        copy(profiles = profiles.filterNot { it.id == id })
    }

    companion object {
        const val XBOX_ID = "button-layout-xbox"
        const val NINTENDO_ID = "button-layout-nintendo"
        val factoryIds: Set<String> = setOf(XBOX_ID, NINTENDO_ID)

        fun factoryProfiles(): List<ButtonLayoutProfile> = listOf(
            factoryProfile(NINTENDO_ID),
            factoryProfile(XBOX_ID),
        )

        private fun factoryProfile(id: String): ButtonLayoutProfile = ButtonLayoutProfile(
            id = id,
            name = requireNotNull(
                BundledDefaultConfig.itemString("button-layout-profile", id, "name")
            ),
            layout = FaceButtonLayout.valueOf(requireNotNull(
                BundledDefaultConfig.itemString("button-layout-profile", id, "layout")
            ).uppercase()),
            m1 = GamepadButtonMapping.valueOf(requireNotNull(
                BundledDefaultConfig.itemString("button-layout-profile", id, "m1")
            ).uppercase()),
            m2 = GamepadButtonMapping.valueOf(requireNotNull(
                BundledDefaultConfig.itemString("button-layout-profile", id, "m2")
            ).uppercase()),
            triggerMode = GamepadTriggerMode.valueOf(requireNotNull(
                BundledDefaultConfig.itemString("button-layout-profile", id, "triggerMode")
            ).uppercase()),
        )
    }
}

object ButtonLayoutProfilePreferences {
    fun load(prefs: SharedPreferences): ButtonLayoutProfileCatalog {
        val stored = prefs.getString(Prefs.BUTTON_LAYOUT_PROFILE_CATALOG, null)
        val decoded = stored?.let(::decode) ?: ButtonLayoutProfileCatalog()
        val customProfiles = decoded.profiles
            .filterNot { it.id in ButtonLayoutProfileCatalog.factoryIds }
            .distinctBy(ButtonLayoutProfile::id)
            .map(ButtonLayoutProfile::normalized)
        val normalized = ButtonLayoutProfileCatalog(
            ButtonLayoutProfileCatalog.factoryProfiles() + customProfiles,
        )
        if (stored == null || decoded != normalized) persist(prefs, normalized)
        return normalized
    }

    fun add(
        prefs: SharedPreferences,
        name: String,
    ): Pair<ButtonLayoutProfileCatalog, String> {
        val current = load(prefs)
        val id = "button-layout-${UUID.randomUUID()}"
        val cleanName = name.trim().take(40).ifBlank { "New button layout" }
        val profile = ButtonLayoutProfile(id = id, name = cleanName)
        return current.plus(profile).also { persist(prefs, it) } to id
    }

    fun addImported(
        prefs: SharedPreferences,
        profile: ButtonLayoutProfile,
    ): ButtonLayoutProfileCatalog {
        val imported = profile.copy(id = "button-layout-${UUID.randomUUID()}").normalized()
        return load(prefs).plus(imported).also { persist(prefs, it) }
    }

    fun rename(
        prefs: SharedPreferences,
        profileId: String,
        name: String,
    ): ButtonLayoutProfileCatalog = if (profileId in ButtonLayoutProfileCatalog.factoryIds) {
        load(prefs)
    } else {
        update(prefs, profileId) { it.renamed(name) }
    }

    fun setLayout(
        prefs: SharedPreferences,
        profileId: String,
        layout: FaceButtonLayout,
    ): ButtonLayoutProfileCatalog = if (profileId in ButtonLayoutProfileCatalog.factoryIds) {
        load(prefs)
    } else {
        update(prefs, profileId) { it.copy(layout = layout) }
    }

    fun setM1(
        prefs: SharedPreferences,
        profileId: String,
        mapping: GamepadButtonMapping,
    ): ButtonLayoutProfileCatalog = update(prefs, profileId) { it.copy(m1 = mapping) }

    fun setM2(
        prefs: SharedPreferences,
        profileId: String,
        mapping: GamepadButtonMapping,
    ): ButtonLayoutProfileCatalog = update(prefs, profileId) { it.copy(m2 = mapping) }

    fun setTriggerMode(
        prefs: SharedPreferences,
        profileId: String,
        triggerMode: GamepadTriggerMode,
    ): ButtonLayoutProfileCatalog = update(prefs, profileId) {
        it.copy(triggerMode = triggerMode)
    }

    fun delete(prefs: SharedPreferences, profileId: String): ButtonLayoutProfileCatalog {
        if (profileId in ButtonLayoutProfileCatalog.factoryIds) return load(prefs)
        val updated = load(prefs).remove(profileId)
        persist(prefs, updated)
        return updated
    }

    fun resolveEffectiveProfile(
        prefs: SharedPreferences,
        foregroundPackageName: String?,
        foregroundIsGame: Boolean = false,
    ): ButtonLayoutProfile? {
        val catalog = load(prefs)
        if (foregroundPackageName == null) {
            ButtonLayoutTilePreferences.selectedProfileId(prefs, catalog)?.let { tileProfileId ->
                return catalog.profile(tileProfileId)
            }
        }
        val buttonLayoutIds = catalog.profiles.mapTo(mutableSetOf(), ButtonLayoutProfile::id)
        val fanIds = FanCurvePreferences.load(prefs).catalog.profiles
            .mapTo(mutableSetOf()) { it.id }
        val joystickIds = JoystickProfilePreferences.load(prefs).profiles
            .mapTo(mutableSetOf()) { it.id }
        val presetConfig = PresetPreferences.load(
            prefs,
            fanIds,
            joystickIds,
            availableButtonLayoutProfileIds = buttonLayoutIds,
        )
        val appProfiles = AppProfilePreferences.load(
            prefs = prefs,
            availablePresetIds = presetConfig.catalog.presets.mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = fanIds,
            availableJoystickProfileIds = joystickIds,
            availableButtonLayoutProfileIds = buttonLayoutIds,
        )
        return catalog.profile(
            resolveTargetProfileId(
                appProfile = foregroundPackageName?.let(appProfiles::get),
                presetConfig = presetConfig,
                catalog = catalog,
                appIsGame = foregroundIsGame,
            ),
        )
    }

    internal fun resolveTargetProfileId(
        appProfile: AppControlProfile?,
        presetConfig: ControlPresetConfig,
        catalog: ButtonLayoutProfileCatalog,
        appIsGame: Boolean = false,
    ): String? = appProfile?.buttonLayoutId
        ?.takeIf { catalog.profile(it) != null }
        ?: AppProfilePreferences.effectivePreset(appProfile, presetConfig, appIsGame)
            .buttonLayoutId
            ?.takeIf { catalog.profile(it) != null }

    private fun update(
        prefs: SharedPreferences,
        profileId: String,
        transform: (ButtonLayoutProfile) -> ButtonLayoutProfile,
    ): ButtonLayoutProfileCatalog {
        val current = load(prefs)
        val target = current.profile(profileId) ?: return current
        return current.replace(transform(target)).also { persist(prefs, it) }
    }

    private fun persist(prefs: SharedPreferences, catalog: ButtonLayoutProfileCatalog) {
        val profiles = JSONArray()
        catalog.profiles.forEach { profile ->
            profiles.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("layout", profile.layout.sysfsValue)
                    .put("m1", profile.m1.sysfsValue)
                    .put("m2", profile.m2.sysfsValue)
                    .put("triggerMode", profile.triggerMode.sysfsValue),
            )
        }
        prefs.edit {
            putString(
                Prefs.BUTTON_LAYOUT_PROFILE_CATALOG,
                JSONObject().put("profiles", profiles).toString(),
            )
        }
    }

    private fun decode(serialized: String): ButtonLayoutProfileCatalog = runCatching {
        val entries = JSONObject(serialized).getJSONArray("profiles")
        ButtonLayoutProfileCatalog(
            buildList {
                repeat(entries.length()) { index ->
                    val item = entries.getJSONObject(index)
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: return@repeat
                    val name = item.optString("name").takeIf(String::isNotBlank)
                        ?: return@repeat
                    val layout = FaceButtonLayout.entries.firstOrNull {
                        it.sysfsValue == item.optString("layout")
                    } ?: return@repeat
                    val m1 = GamepadButtonMapping.entries.firstOrNull {
                        it.sysfsValue == item.optString("m1")
                    } ?: GamepadButtonMapping.NONE
                    val m2 = GamepadButtonMapping.entries.firstOrNull {
                        it.sysfsValue == item.optString("m2")
                    } ?: GamepadButtonMapping.NONE
                    val triggerMode = GamepadTriggerMode.entries.firstOrNull {
                        it.sysfsValue == item.optString(
                            "triggerMode",
                            GamepadTriggerMode.BOTH.sysfsValue,
                        )
                    } ?: GamepadTriggerMode.BOTH
                    add(
                        ButtonLayoutProfile(
                            id,
                            name,
                            layout,
                            m1,
                            m2,
                            triggerMode,
                        ).normalized(),
                    )
                }
            },
        )
    }.getOrElse { ButtonLayoutProfileCatalog() }
}

/** A profile selected from Quick Settings overrides app/preset button automation. */
object ButtonLayoutTilePreferences {
    fun selectedProfileId(
        prefs: SharedPreferences,
        catalog: ButtonLayoutProfileCatalog,
    ): String? = prefs.getString(
        Prefs.BUTTON_LAYOUT_TILE_PROFILE,
        BundledDefaultConfig.nullableSettingString("buttonLayoutTileId"),
    )
        ?.takeIf { catalog.profile(it) != null }

    fun select(prefs: SharedPreferences, profileId: String) {
        prefs.edit { putString(Prefs.BUTTON_LAYOUT_TILE_PROFILE, profileId) }
    }

    fun clearSelection(prefs: SharedPreferences) {
        prefs.edit { remove(Prefs.BUTTON_LAYOUT_TILE_PROFILE) }
    }

    fun selectNext(
        prefs: SharedPreferences,
        catalog: ButtonLayoutProfileCatalog,
    ): ButtonLayoutProfile? {
        val currentId = selectedProfileId(prefs, catalog)
        val next = nextProfile(catalog, currentId) ?: return null
        select(prefs, next.id)
        return next
    }

    internal fun nextProfile(
        catalog: ButtonLayoutProfileCatalog,
        currentId: String?,
    ): ButtonLayoutProfile? {
        if (catalog.profiles.isEmpty()) return null
        val currentIndex = catalog.profiles.indexOfFirst { it.id == currentId }
        return catalog.profiles[(currentIndex + 1).mod(catalog.profiles.size)]
    }
}
