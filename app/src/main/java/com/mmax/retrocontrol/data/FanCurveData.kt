package com.mmax.retrocontrol.data

import kotlin.math.roundToInt

/** At [tempC]°C, request the normalized fan output [speedPercent] (0..100). */
data class FanCurvePoint(val tempC: Int, val speedPercent: Int) {
    init {
        require(tempC in 20..100) { "tempC must be in 20..100" }
        require(speedPercent in 0..100) { "speedPercent must be in 0..100" }
    }
}

/** Stable identities for factory and migrated curves. Names are resolved by the UI layer. */
enum class BuiltInFanCurve(
    val id: String,
    val factorySerialized: String,
    val initialSerialized: String = factorySerialized,
) {
    QUIET(
        "quiet",
        BundledDefaultConfig.fanCurveSerialized("quiet", "defaultPoints"),
        BundledDefaultConfig.fanCurveSerialized("quiet", "points"),
    ),
    NORMAL(
        "normal",
        BundledDefaultConfig.fanCurveSerialized("normal", "defaultPoints"),
        BundledDefaultConfig.fanCurveSerialized("normal", "points"),
    ),
    PERFORMANCE(
        "performance",
        BundledDefaultConfig.fanCurveSerialized("performance", "defaultPoints"),
        BundledDefaultConfig.fanCurveSerialized("performance", "points"),
    ),
    LEGACY_CUSTOM("legacy-custom", "percent|50:10,65:10,80:25,90:50");

    val factoryPoints: List<FanCurvePoint> by lazy {
        FanCurveSerializer.parse(factorySerialized)
    }

    val initialPoints: List<FanCurvePoint> by lazy {
        FanCurveSerializer.parse(initialSerialized)
    }

    companion object {
        fun fromId(id: String?): BuiltInFanCurve? = entries.firstOrNull { it.id == id }
    }
}

data class FanCurveProfile(
    val id: String,
    val builtIn: BuiltInFanCurve? = null,
    /** Null means the host should display the localized built-in name. */
    val customName: String? = null,
    val points: List<FanCurvePoint>,
    /** User-controlled reset baseline for this individual curve. */
    val defaultPoints: List<FanCurvePoint>,
) {
    init {
        require(id.isNotBlank()) { "A fan curve requires an id" }
        require(FanCurveSerializer.sanitize(points).size >= 2) {
            "A fan curve requires at least two points"
        }
        require(FanCurveSerializer.sanitize(defaultPoints).size >= 2) {
            "A fan curve default requires at least two points"
        }
    }

    fun withPoints(value: List<FanCurvePoint>): FanCurveProfile =
        copy(points = requireCurve(value))

    fun withCurrentAsDefault(value: List<FanCurvePoint> = points): FanCurveProfile {
        val clean = requireCurve(value)
        return copy(points = clean, defaultPoints = clean)
    }

    fun reset(): FanCurveProfile = copy(points = defaultPoints)

    fun renamed(value: String): FanCurveProfile =
        copy(customName = value.trim().take(40).ifBlank { null })

    private fun requireCurve(value: List<FanCurvePoint>): List<FanCurvePoint> =
        FanCurveSerializer.sanitize(value).also {
            require(it.size >= 2) { "A fan curve requires at least two points" }
        }
}

data class FanCurveCatalog(
    val profiles: List<FanCurveProfile> = factoryProfiles(),
) {
    fun profile(id: String?): FanCurveProfile? = profiles.firstOrNull { it.id == id }

    fun replace(profile: FanCurveProfile): FanCurveCatalog =
        copy(
            profiles = profiles.map { existing ->
                if (existing.id == profile.id) profile else existing
            }
        )

    fun plus(profile: FanCurveProfile): FanCurveCatalog =
        copy(profiles = profiles.filterNot { it.id == profile.id } + profile)

    fun remove(id: String): FanCurveCatalog =
        copy(profiles = profiles.filterNot { it.id == id })

    companion object {
        fun factoryProfiles(): List<FanCurveProfile> =
            listOf(
                BuiltInFanCurve.QUIET,
                BuiltInFanCurve.NORMAL,
                BuiltInFanCurve.PERFORMANCE,
            ).map { builtIn ->
                FanCurveProfile(
                    id = builtIn.id,
                    builtIn = builtIn,
                    points = builtIn.initialPoints,
                    defaultPoints = builtIn.factoryPoints,
                )
            }
    }
}

data class FanControlConfig(
    val catalog: FanCurveCatalog = FanCurveCatalog(),
    /** Null means fan control is off. */
    val activeProfileId: String? = BundledDefaultConfig
        .nullableSettingString("fanCurveId")
        .takeIf { BundledDefaultConfig.settingBoolean("fanEnabled") },
) {
    val activeProfile: FanCurveProfile? get() = catalog.profile(activeProfileId)
    val enabled: Boolean get() = activeProfile != null
}

object FanCurveSerializer {
    private const val PERCENT_PREFIX = "percent|"

    fun serialize(points: List<FanCurvePoint>): String =
        PERCENT_PREFIX + sanitize(points).joinToString(",") {
            "${it.tempC}:${it.speedPercent}"
        }

    fun parse(serialized: String?): List<FanCurvePoint> {
        val parsed = decode(serialized)
        return if (parsed.size >= 2) {
            parsed
        } else {
            val fallback = decode(BuiltInFanCurve.NORMAL.factorySerialized)
            if (fallback.size >= 2) fallback else listOf(
                FanCurvePoint(30, 13),
                FanCurvePoint(85, 100),
            )
        }
    }

    fun parse(serialized: String?, fallback: List<FanCurvePoint>): List<FanCurvePoint> {
        val parsed = decode(serialized)
        return if (parsed.size >= 2) parsed else sanitize(fallback)
    }

    private fun decode(serialized: String?): List<FanCurvePoint> {
        if (serialized.isNullOrBlank()) return emptyList()
        val storesPercent = serialized.startsWith(PERCENT_PREFIX)
        val body = serialized.removePrefix(PERCENT_PREFIX)
        val parsed = body.split(",").mapNotNull { entry ->
            val parts = entry.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val temp = parts[0].trim().toIntOrNull()?.coerceIn(20, 100)
                ?: return@mapNotNull null
            val rawSpeed = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
            val speedPercent = if (storesPercent) {
                rawSpeed.coerceIn(0, 100)
            } else {
                (rawSpeed.coerceIn(0, 8) * 100.0 / 8.0).roundToInt()
            }
            FanCurvePoint(temp, speedPercent)
        }
        return sanitize(parsed)
    }

    fun sanitize(points: List<FanCurvePoint>): List<FanCurvePoint> =
        points
            .sortedBy { it.tempC }
            .distinctBy { it.tempC }

    fun interpolate(tempC: Double, points: List<FanCurvePoint>): Double {
        val sorted = sanitize(points)
        if (sorted.isEmpty()) return 0.0
        if (tempC < sorted.first().tempC) return 0.0
        if (sorted.size == 1 || tempC == sorted.first().tempC.toDouble()) {
            return sorted.first().speedPercent.toDouble()
        }
        if (tempC >= sorted.last().tempC) return sorted.last().speedPercent.toDouble()

        val upperIndex = sorted.indexOfFirst { tempC <= it.tempC }
        val lower = sorted[upperIndex - 1]
        val upper = sorted[upperIndex]
        val ratio = (tempC - lower.tempC) / (upper.tempC - lower.tempC)
        return lower.speedPercent + ratio * (upper.speedPercent - lower.speedPercent)
    }
}
