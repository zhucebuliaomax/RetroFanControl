package com.mmax.retrocontrol.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.mmax.retrocontrol.feature.joystick.JoystickRgbMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class JoystickProfile(
    val id: String,
    val name: String,
    val mode: JoystickRgbMode = JoystickRgbMode.STATIC,
    val red: Int = 255,
    val green: Int = 100,
    val blue: Int = 0,
    val brightness: Int = 198,
) {
    init {
        require(id.isNotBlank()) { "A joystick profile requires an id" }
        require(name.isNotBlank()) { "A joystick profile requires a name" }
    }

    fun normalized(): JoystickProfile = copy(
        name = name.trim().take(40).ifBlank { "Profile" },
        red = red.coerceIn(0, 255),
        green = green.coerceIn(0, 255),
        blue = blue.coerceIn(0, 255),
        brightness = brightness.coerceIn(0, 255),
    )

    fun renamed(value: String): JoystickProfile =
        copy(name = value.trim().take(40).ifBlank { name })
}

data class JoystickProfileCatalog(
    val profiles: List<JoystickProfile> = emptyList(),
) {
    fun profile(id: String?): JoystickProfile? = profiles.firstOrNull { it.id == id }

    fun replace(profile: JoystickProfile): JoystickProfileCatalog = copy(
        profiles = profiles.map { existing ->
            if (existing.id == profile.id) profile.normalized() else existing
        }
    )

    fun plus(profile: JoystickProfile): JoystickProfileCatalog = copy(
        profiles = profiles.filterNot { it.id == profile.id } + profile.normalized()
    )

    fun remove(id: String): JoystickProfileCatalog =
        copy(profiles = profiles.filterNot { it.id == id })

}

sealed interface JoystickSelectionSource {
    data object FollowProfile : JoystickSelectionSource
    data class DirectProfile(val profileId: String) : JoystickSelectionSource
}

data class JoystickSelectionConfig(
    val source: JoystickSelectionSource = JoystickSelectionSource.FollowProfile,
    val enabled: Boolean = true,
)

object JoystickSelectionPreferences {
    private const val SOURCE_PROFILE = "PROFILE"
    private const val SOURCE_DIRECT = "DIRECT"

    fun load(prefs: SharedPreferences, catalog: JoystickProfileCatalog): JoystickSelectionConfig {
        val source = when (prefs.getString(Prefs.JOYSTICK_SELECTION_SOURCE, null)) {
            SOURCE_DIRECT -> prefs.getString(Prefs.JOYSTICK_SELECTION_PROFILE, null)
                ?.takeIf { catalog.profile(it) != null }
                ?.let(JoystickSelectionSource::DirectProfile)
                ?: JoystickSelectionSource.FollowProfile
            else -> JoystickSelectionSource.FollowProfile
        }
        return JoystickSelectionConfig(
            source = source,
            enabled = prefs.getBoolean(Prefs.JOYSTICK_TILE_ENABLED, true),
        ).also { config ->
            if (!prefs.contains(Prefs.JOYSTICK_SELECTION_SOURCE)) persist(prefs, config)
        }
    }

    fun selectFollowProfile(prefs: SharedPreferences) {
        persist(prefs, JoystickSelectionConfig())
    }

    fun selectDirectProfile(prefs: SharedPreferences, profileId: String) {
        val catalog = JoystickProfilePreferences.load(prefs)
        if (catalog.profile(profileId) == null) return
        persist(
            prefs,
            JoystickSelectionConfig(JoystickSelectionSource.DirectProfile(profileId), true),
        )
    }

    fun toggle(prefs: SharedPreferences): JoystickSelectionConfig {
        val current = load(prefs, JoystickProfilePreferences.load(prefs))
        return current.copy(enabled = !current.enabled).also { persist(prefs, it) }
    }

    private fun persist(prefs: SharedPreferences, config: JoystickSelectionConfig) {
        prefs.edit {
            putString(
                Prefs.JOYSTICK_SELECTION_SOURCE,
                if (config.source is JoystickSelectionSource.FollowProfile) {
                    SOURCE_PROFILE
                } else {
                    SOURCE_DIRECT
                },
            )
            putString(
                Prefs.JOYSTICK_SELECTION_PROFILE,
                (config.source as? JoystickSelectionSource.DirectProfile)?.profileId,
            )
            putBoolean(Prefs.JOYSTICK_TILE_ENABLED, config.enabled)
        }
    }
}

object JoystickProfilePreferences {
    fun load(prefs: SharedPreferences): JoystickProfileCatalog {
        val stored = prefs.getString(Prefs.JOYSTICK_PROFILE_CATALOG, null)
        val decoded = stored?.let(::decode) ?: JoystickProfileCatalog()
        val normalized = JoystickProfileCatalog(
            decoded.profiles.distinctBy(JoystickProfile::id).map(JoystickProfile::normalized)
        )
        if (stored == null || hasLegacyArchivedField(stored) || decoded != normalized) {
            persist(prefs, normalized)
        }
        return normalized
    }

    fun add(prefs: SharedPreferences, name: String): Pair<JoystickProfileCatalog, String> {
        val current = load(prefs)
        val template = current.profiles.lastOrNull()
        val id = "joystick-${UUID.randomUUID()}"
        val profile = (template ?: JoystickProfile(id = id, name = name)).copy(
            id = id,
            name = name.trim().take(40).ifBlank { "New profile" },
        )
        return current.plus(profile).also { persist(prefs, it) } to id
    }

    fun addImported(
        prefs: SharedPreferences,
        profile: JoystickProfile,
    ): JoystickProfileCatalog {
        val imported = profile.copy(id = "joystick-${UUID.randomUUID()}").normalized()
        return load(prefs).plus(imported).also { persist(prefs, it) }
    }

    fun rename(
        prefs: SharedPreferences,
        profileId: String,
        name: String,
    ): JoystickProfileCatalog = update(prefs, profileId) { it.renamed(name) }

    fun setMode(
        prefs: SharedPreferences,
        profileId: String,
        mode: JoystickRgbMode,
    ): JoystickProfileCatalog = update(prefs, profileId) { it.copy(mode = mode) }

    fun setColor(
        prefs: SharedPreferences,
        profileId: String,
        red: Int,
        green: Int,
        blue: Int,
    ): JoystickProfileCatalog = update(prefs, profileId) {
        it.copy(red = red, green = green, blue = blue).normalized()
    }

    fun setBrightness(
        prefs: SharedPreferences,
        profileId: String,
        brightness: Int,
    ): JoystickProfileCatalog = update(prefs, profileId) {
        it.copy(brightness = brightness.coerceIn(0, 255))
    }

    fun delete(prefs: SharedPreferences, profileId: String): JoystickProfileCatalog {
        val updated = load(prefs).remove(profileId)
        persist(prefs, updated)
        return updated
    }

    fun resolveEffectiveProfile(
        prefs: SharedPreferences,
        foregroundPackageName: String?,
        foregroundIsGame: Boolean = false,
    ): JoystickProfile? {
        val catalog = load(prefs)
        val tileSelection = JoystickSelectionPreferences.load(prefs, catalog)
        if (!tileSelection.enabled) return null
        val joystickIds = catalog.profiles.mapTo(mutableSetOf(), JoystickProfile::id)
        val fanIds = FanCurvePreferences.load(prefs).catalog.profiles
            .mapTo(mutableSetOf()) { it.id }
        val presetConfig = PresetPreferences.load(prefs, fanIds, joystickIds)
        val appProfiles = AppProfilePreferences.load(
            prefs = prefs,
            availablePresetIds = presetConfig.catalog.presets.mapTo(mutableSetOf()) { it.id },
            availableFanCurveIds = fanIds,
            availableJoystickProfileIds = joystickIds,
        )
        val appProfile = foregroundPackageName?.let(appProfiles::get)
        if (foregroundPackageName == null) {
            (tileSelection.source as? JoystickSelectionSource.DirectProfile)?.let { direct ->
                return catalog.profile(direct.profileId)
            }
        }
        val targetId = appProfile?.joystickId
            ?.takeIf(joystickIds::contains)
            ?: AppProfilePreferences.effectivePreset(
                appProfile, presetConfig, foregroundIsGame,
            ).joystickId
                ?.takeIf(joystickIds::contains)
        return catalog.profile(targetId)
    }

    private fun update(
        prefs: SharedPreferences,
        profileId: String,
        transform: (JoystickProfile) -> JoystickProfile,
    ): JoystickProfileCatalog {
        val current = load(prefs)
        val target = current.profile(profileId) ?: return current
        return current.replace(transform(target)).also { persist(prefs, it) }
    }

    private fun persist(prefs: SharedPreferences, catalog: JoystickProfileCatalog) {
        prefs.edit { putString(Prefs.JOYSTICK_PROFILE_CATALOG, encode(catalog)) }
    }

    private fun encode(catalog: JoystickProfileCatalog): String {
        val profiles = JSONArray()
        catalog.profiles.forEach { profile ->
            profiles.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("mode", profile.mode.name)
                    .put("red", profile.red)
                    .put("green", profile.green)
                    .put("blue", profile.blue)
                    .put("brightness", profile.brightness)
            )
        }
        return JSONObject().put("profiles", profiles).toString()
    }

    private fun hasLegacyArchivedField(value: String): Boolean = runCatching {
        val entries = JSONObject(value).getJSONArray("profiles")
        (0 until entries.length()).any { index ->
            entries.getJSONObject(index).has("archived")
        }
    }.getOrDefault(false)

    private fun decode(serialized: String): JoystickProfileCatalog = runCatching {
        val array = JSONObject(serialized).getJSONArray("profiles")
        val profiles = buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                if (item.optBoolean("archived", false)) return@repeat
                val id = item.optString("id").takeIf(String::isNotBlank) ?: return@repeat
                val name = item.optString("name").takeIf(String::isNotBlank) ?: return@repeat
                val mode = runCatching {
                    JoystickRgbMode.valueOf(item.optString("mode"))
                }.getOrDefault(JoystickRgbMode.STATIC)
                add(
                    JoystickProfile(
                        id = id,
                        name = name,
                        mode = mode,
                        red = item.optInt("red", 255),
                        green = item.optInt("green", 100),
                        blue = item.optInt("blue", 0),
                        brightness = item.optInt("brightness", 198),
                    ).normalized()
                )
            }
        }
        JoystickProfileCatalog(profiles)
    }.getOrElse { JoystickProfileCatalog() }
}
