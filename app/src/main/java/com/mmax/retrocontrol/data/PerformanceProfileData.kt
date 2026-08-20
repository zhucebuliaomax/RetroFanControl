package com.mmax.retrocontrol.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.mmax.retrocontrol.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

data class CpuFrequencyPolicy(
    val id: Int,
    val cpuIds: List<Int>,
    val supportedFrequencies: List<Int>,
    val currentMinFrequency: Int,
    val currentMaxFrequency: Int,
    val stockMinFrequency: Int,
    val stockMaxFrequency: Int,
) {
    val path: String
        get() = "/sys/devices/system/cpu/cpufreq/policy$id"

    val scalingMaxPath: String
        get() = "$path/scaling_max_freq"
}

enum class BuiltInPerformanceProfile(
    val id: String,
    val maximumRatio: Double?,
) {
    STOCK("performance-stock", null),
    BALANCED("performance-balanced", 0.85),
    EFFICIENT("performance-efficient", 0.70),
    BATTERY_SAVER("performance-battery-saver", 0.55),
}

data class PerformanceProfile(
    val id: String,
    val maxFrequencies: Map<Int, Int>,
    val builtIn: BuiltInPerformanceProfile? = null,
    val customName: String? = null,
) {
    init {
        require(id.isNotBlank()) { "A performance profile requires an id" }
        require(builtIn != null || !customName.isNullOrBlank()) {
            "A custom performance profile requires a name"
        }
    }

    val isStock: Boolean
        get() = builtIn == BuiltInPerformanceProfile.STOCK

    val isEditable: Boolean
        get() = builtIn == null

    fun renamed(value: String): PerformanceProfile = if (!isEditable) {
        this
    } else {
        copy(customName = value.trim().take(40).ifBlank { customName })
    }
}

data class PerformanceProfileConfig(
    val policies: List<CpuFrequencyPolicy> = emptyList(),
    val profiles: List<PerformanceProfile> = emptyList(),
) {
    fun profile(id: String?): PerformanceProfile? = profiles.firstOrNull { it.id == id }

    val stockProfile: PerformanceProfile?
        get() = profile(BuiltInPerformanceProfile.STOCK.id)
}

fun PerformanceProfile.displayName(context: Context): String = when (builtIn) {
    BuiltInPerformanceProfile.STOCK -> context.getString(R.string.performance_stock)
    BuiltInPerformanceProfile.BALANCED -> context.getString(R.string.performance_balanced)
    BuiltInPerformanceProfile.EFFICIENT -> context.getString(R.string.performance_efficient)
    BuiltInPerformanceProfile.BATTERY_SAVER -> context.getString(R.string.performance_battery_saver)
    null -> customName.orEmpty()
}

object PerformanceProfilePreferences {
    fun load(
        prefs: SharedPreferences,
        policies: List<CpuFrequencyPolicy>,
    ): PerformanceProfileConfig {
        val customProfiles = decode(prefs.getString(Prefs.PERFORMANCE_PROFILE_CATALOG, null))
            .mapNotNull { profile -> normalize(profile, policies) }
        val profiles = if (policies.isEmpty()) {
            customProfiles
        } else {
            factoryProfiles(policies) + customProfiles
        }
        val config = PerformanceProfileConfig(policies = policies, profiles = profiles)
        if (policies.isNotEmpty()) persist(prefs, customProfiles)
        return config
    }

    fun add(
        prefs: SharedPreferences,
        policies: List<CpuFrequencyPolicy>,
        name: String,
    ): Pair<PerformanceProfileConfig, String>? {
        if (policies.isEmpty()) return null
        val current = load(prefs, policies)
        val id = "performance-${UUID.randomUUID()}"
        val profile = PerformanceProfile(
            id = id,
            customName = name.trim().take(40).ifBlank { "New performance profile" },
            maxFrequencies = policies.associate { policy ->
                policy.id to policy.supportedFrequencies.lastOrNull()
                    .orPositive(policy.currentMaxFrequency)
                    .orPositive(policy.stockMaxFrequency)
            },
        )
        val customProfiles = current.profiles.filter(PerformanceProfile::isEditable) + profile
        persist(prefs, customProfiles)
        return load(prefs, policies) to id
    }

    fun addFromTemplate(
        prefs: SharedPreferences,
        policies: List<CpuFrequencyPolicy>,
        name: String,
        maxFrequencies: Map<Int, Int>,
    ): Pair<PerformanceProfileConfig, String>? {
        if (policies.isEmpty()) return null
        val current = load(prefs, policies)
        val id = "performance-${UUID.randomUUID()}"
        val profile = normalize(
            PerformanceProfile(
                id = id,
                customName = name.trim().take(40).ifBlank { "Frequency profile" },
                maxFrequencies = maxFrequencies,
            ),
            policies,
        ) ?: return null
        persist(
            prefs,
            current.profiles.filter(PerformanceProfile::isEditable) + profile,
        )
        return load(prefs, policies) to id
    }

    fun addImported(
        prefs: SharedPreferences,
        policies: List<CpuFrequencyPolicy>,
        name: String,
        maxFrequencies: Map<Int, Int>,
    ): PerformanceProfileConfig {
        val current = load(prefs, policies)
        val imported = PerformanceProfile(
            id = "performance-${UUID.randomUUID()}",
            customName = name.trim().take(40).ifBlank { "Performance profile" },
            maxFrequencies = maxFrequencies,
        )
        val normalized = normalize(imported, policies) ?: imported
        persist(
            prefs,
            current.profiles.filter(PerformanceProfile::isEditable) + normalized,
        )
        return load(prefs, policies)
    }

    fun update(
        prefs: SharedPreferences,
        policies: List<CpuFrequencyPolicy>,
        profileId: String,
        name: String,
        maxFrequencies: Map<Int, Int>,
    ): PerformanceProfileConfig {
        val current = load(prefs, policies)
        val target = current.profile(profileId)?.takeIf(PerformanceProfile::isEditable)
            ?: return current
        val replacement = normalize(
            target.renamed(name).copy(maxFrequencies = maxFrequencies),
            policies,
        ) ?: return current
        persist(
            prefs,
            current.profiles.filter(PerformanceProfile::isEditable).map { profile ->
                if (profile.id == profileId) replacement else profile
            },
        )
        return load(prefs, policies)
    }

    fun delete(
        prefs: SharedPreferences,
        policies: List<CpuFrequencyPolicy>,
        profileId: String,
    ): PerformanceProfileConfig {
        val current = load(prefs, policies)
        if (current.profile(profileId)?.isEditable != true) return current
        persist(
            prefs,
            current.profiles.filter { it.isEditable && it.id != profileId },
        )
        return load(prefs, policies)
    }

    internal fun factoryProfiles(
        policies: List<CpuFrequencyPolicy>,
    ): List<PerformanceProfile> = BuiltInPerformanceProfile.entries.map { builtIn ->
        PerformanceProfile(
            id = builtIn.id,
            builtIn = builtIn,
            maxFrequencies = policies.associate { policy ->
                policy.id to if (builtIn == BuiltInPerformanceProfile.STOCK) {
                    policy.stockMaxFrequency
                } else {
                    ratioFrequency(policy, requireNotNull(builtIn.maximumRatio))
                }
            },
        )
    }

    internal fun ratioFrequency(policy: CpuFrequencyPolicy, ratio: Double): Int {
        val frequencies = policy.supportedFrequencies
        if (frequencies.isEmpty()) return policy.currentMaxFrequency
        val target = frequencies.last() * ratio.coerceIn(0.0, 1.0)
        return frequencies.minBy { frequency -> abs(frequency - target) }
    }

    private fun normalize(
        profile: PerformanceProfile,
        policies: List<CpuFrequencyPolicy>,
    ): PerformanceProfile? {
        if (!profile.isEditable) return null
        if (policies.isEmpty()) return profile
        val normalized = policies.associate { policy ->
            val requested = profile.maxFrequencies[policy.id]
                ?: policy.supportedFrequencies.lastOrNull()
                ?: policy.currentMaxFrequency
            policy.id to nearestSupported(policy, requested)
        }
        return profile.copy(maxFrequencies = normalized)
    }

    private fun nearestSupported(policy: CpuFrequencyPolicy, requested: Int): Int {
        return policy.supportedFrequencies.minByOrNull { frequency ->
            abs(frequency.toLong() - requested.toLong())
        } ?: requested.coerceAtLeast(1)
    }

    private fun persist(
        prefs: SharedPreferences,
        customProfiles: List<PerformanceProfile>,
    ) {
        val entries = JSONArray()
        customProfiles.forEach { profile ->
            val frequencies = JSONObject()
            profile.maxFrequencies.toSortedMap().forEach { (policyId, frequency) ->
                frequencies.put(policyId.toString(), frequency)
            }
            entries.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.customName)
                    .put("maxFrequencies", frequencies)
            )
        }
        prefs.edit {
            putString(
                Prefs.PERFORMANCE_PROFILE_CATALOG,
                JSONObject().put("profiles", entries).toString(),
            )
        }
    }

    private fun decode(serialized: String?): List<PerformanceProfile> {
        if (serialized.isNullOrBlank()) return emptyList()
        return runCatching {
            val entries = JSONObject(serialized).getJSONArray("profiles")
            buildList {
                repeat(entries.length()) { index ->
                    val item = entries.getJSONObject(index)
                    val id = item.optString("id").takeIf(String::isNotBlank)
                        ?: return@repeat
                    val name = item.optString("name").takeIf(String::isNotBlank)
                        ?: return@repeat
                    val encodedFrequencies = item.optJSONObject("maxFrequencies")
                        ?: return@repeat
                    val frequencies = buildMap {
                        encodedFrequencies.keys().forEach { policyId ->
                            val idValue = policyId.toIntOrNull() ?: return@forEach
                            val frequency = encodedFrequencies.optInt(policyId, 0)
                            if (frequency > 0) put(idValue, frequency)
                        }
                    }
                    if (frequencies.isNotEmpty()) {
                        add(
                            PerformanceProfile(
                                id = id,
                                customName = name,
                                maxFrequencies = frequencies,
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun Int?.orPositive(fallback: Int): Int =
        this?.takeIf { it > 0 } ?: fallback
}

object PerformanceProfileResolver {
    fun resolveTargetProfileId(
        profileConfig: PerformanceProfileConfig,
        presetConfig: ControlPresetConfig,
        appProfile: AppControlProfile? = null,
        appIsGame: Boolean = false,
    ): String? {
        val availableIds = profileConfig.profiles.mapTo(mutableSetOf()) { it.id }
        return appProfile?.performanceProfileId
            ?.takeIf(availableIds::contains)
            ?: AppProfilePreferences.effectivePreset(
                appProfile,
                presetConfig,
                appIsGame,
            ).performanceProfileId?.takeIf(availableIds::contains)
    }
}

/** A profile selected from Quick Settings overrides app/preset performance automation. */
object PerformanceTilePreferences {
    fun selectedProfileId(
        prefs: SharedPreferences,
        profileConfig: PerformanceProfileConfig,
    ): String? = prefs.getString(
        Prefs.PERFORMANCE_TILE_PROFILE,
        BundledDefaultConfig.nullableSettingString("performanceTileId"),
    )
        ?.takeIf { profileConfig.profile(it) != null }

    fun select(prefs: SharedPreferences, profileId: String) {
        prefs.edit { putString(Prefs.PERFORMANCE_TILE_PROFILE, profileId) }
    }

    fun clearSelection(prefs: SharedPreferences) {
        prefs.edit { remove(Prefs.PERFORMANCE_TILE_PROFILE) }
    }
}
