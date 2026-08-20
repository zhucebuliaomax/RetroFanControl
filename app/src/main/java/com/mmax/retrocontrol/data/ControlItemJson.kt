package com.mmax.retrocontrol.data

import com.mmax.retrocontrol.feature.joystick.JoystickRgbMode
import org.json.JSONArray
import org.json.JSONObject

/** JSON exchange format shared by every import/export entry in Controls. */
object ControlItemJson {
    private const val FORMAT = "retro-control-item"
    private const val VERSION = 1

    sealed interface Item {
        val name: String

        data class Preset(
            override val name: String,
            val value: ControlPreset,
            val fanCurve: FanCurve? = null,
            val joystick: Joystick? = null,
            val buttonLayout: ButtonLayout? = null,
            val performance: Performance? = null,
        ) : Item

        data class FanCurve(
            override val name: String,
            val points: List<FanCurvePoint>,
            val defaultPoints: List<FanCurvePoint>,
        ) : Item

        data class Joystick(
            override val name: String,
            val value: JoystickProfile,
        ) : Item

        data class ButtonLayout(
            override val name: String,
            val value: ButtonLayoutProfile,
        ) : Item

        data class Performance(
            override val name: String,
            val maxFrequencies: Map<Int, Int>,
        ) : Item

        data class AppProfile(
            override val name: String,
            val value: AppControlProfile,
            val preset: Preset? = null,
            val fanCurve: FanCurve? = null,
            val joystick: Joystick? = null,
            val buttonLayout: ButtonLayout? = null,
            val performance: Performance? = null,
        ) : Item
    }

    fun encodePreset(
        preset: ControlPreset,
        fanCurve: Pair<String, FanCurveProfile>? = null,
        joystick: JoystickProfile? = null,
        buttonLayout: ButtonLayoutProfile? = null,
        performance: Pair<String, PerformanceProfile>? = null,
    ): String {
        val data = JSONObject()
            .put("fanCurveId", preset.fanCurveId ?: JSONObject.NULL)
            .put("joystickId", preset.joystickId ?: JSONObject.NULL)
            .put("buttonLayoutId", preset.buttonLayoutId ?: JSONObject.NULL)
            .put("performanceProfileId", preset.performanceProfileId ?: JSONObject.NULL)
        fanCurve?.let { (name, profile) ->
            data.put("fanCurve", encodeFanCurveData(name, profile))
        }
        joystick?.let { data.put("joystick", encodeJoystickData(it)) }
        buttonLayout?.let { data.put("buttonLayout", encodeButtonLayoutData(it)) }
        performance?.let { (name, profile) ->
            data.put("performance", encodePerformanceData(name, profile))
        }
        return root(type = "profile", name = preset.name, data = data)
    }

    fun encodeFanCurve(name: String, profile: FanCurveProfile): String = root(
        type = "fan-curve",
        name = name,
        data = encodeFanCurveData(name, profile),
    )

    fun encodeJoystick(profile: JoystickProfile): String = root(
        type = "joystick-profile",
        name = profile.name,
        data = encodeJoystickData(profile),
    )

    fun encodeButtonLayout(profile: ButtonLayoutProfile): String = root(
        type = "button-layout-profile",
        name = profile.name,
        data = encodeButtonLayoutData(profile),
    )

    fun encodePerformance(name: String, profile: PerformanceProfile): String {
        return root(
            type = "performance-profile",
            name = name,
            data = encodePerformanceData(name, profile),
        )
    }

    fun encodeAppProfile(
        name: String,
        profile: AppControlProfile,
        presetJson: String? = null,
        fanCurveJson: String? = null,
        joystickJson: String? = null,
        buttonLayoutJson: String? = null,
        performanceJson: String? = null,
    ): String {
        val data = JSONObject()
            .put("packageName", profile.packageName)
            .put("presetId", profile.presetId ?: JSONObject.NULL)
            .put("fanCurveId", profile.fanCurveId ?: JSONObject.NULL)
            .put("joystickId", profile.joystickId ?: JSONObject.NULL)
            .put("buttonLayoutId", profile.buttonLayoutId ?: JSONObject.NULL)
            .put("performanceProfileId", profile.performanceProfileId ?: JSONObject.NULL)
        presetJson?.let { data.put("preset", JSONObject(it)) }
        fanCurveJson?.let { data.put("fanCurve", JSONObject(it)) }
        joystickJson?.let { data.put("joystick", JSONObject(it)) }
        buttonLayoutJson?.let { data.put("buttonLayout", JSONObject(it)) }
        performanceJson?.let { data.put("performance", JSONObject(it)) }
        return root(type = "app-profile", name = name, data = data)
    }

    fun decode(json: String): Item {
        val root = JSONObject(json)
        // Accept fan-curve files exported by older versions of the app.
        if (root.optString("format") == "fan-curve") {
            val points = FanCurveJson.decode(json)
            val name = root.optString("name").trim().take(40).ifBlank { "Fan curve" }
            return Item.FanCurve(name, points, points)
        }
        require(root.optString("format") == FORMAT) { "Unsupported control item format" }
        require(root.optInt("version", -1) == VERSION) { "Unsupported control item version" }
        val name = root.getString("name").trim().take(40)
        require(name.isNotBlank()) { "A control item requires a name" }
        val data = root.getJSONObject("data")
        return when (root.getString("type")) {
            "profile" -> {
                val value = ControlPreset(
                    id = "imported",
                    name = name,
                    fanCurveId = data.nullableString("fanCurveId"),
                    joystickId = data.nullableString("joystickId"),
                    buttonLayoutId = data.nullableString("buttonLayoutId"),
                    performanceProfileId = data.nullableString("performanceProfileId"),
                )
                Item.Preset(
                    name = name,
                    value = value,
                    fanCurve = data.optJSONObject("fanCurve")?.let { decodeFanCurveData(it) },
                    joystick = data.optJSONObject("joystick")?.let { decodeJoystickData(it) },
                    buttonLayout = data.optJSONObject("buttonLayout")
                        ?.let { decodeButtonLayoutData(it) },
                    performance = data.optJSONObject("performance")
                        ?.let { decodePerformanceData(it) },
                )
            }
            "fan-curve" -> {
                decodeFanCurveData(data, name)
            }
            "joystick-profile" -> decodeJoystickData(data, name)
            "button-layout-profile" -> decodeButtonLayoutData(data, name)
            "performance-profile" -> decodePerformanceData(data, name)
            "app-profile" -> Item.AppProfile(
                name = name,
                value = AppControlProfile(
                    packageName = data.getString("packageName"),
                    presetId = data.nullableString("presetId"),
                    fanCurveId = data.nullableString("fanCurveId"),
                    joystickId = data.nullableString("joystickId"),
                    buttonLayoutId = data.nullableString("buttonLayoutId"),
                    performanceProfileId = data.nullableString("performanceProfileId"),
                ),
                preset = data.optJSONObject("preset")?.let {
                    decode(it.toString()) as? Item.Preset
                },
                fanCurve = data.optJSONObject("fanCurve")?.let {
                    decode(it.toString()) as? Item.FanCurve
                },
                joystick = data.optJSONObject("joystick")?.let {
                    decode(it.toString()) as? Item.Joystick
                },
                buttonLayout = data.optJSONObject("buttonLayout")?.let {
                    decode(it.toString()) as? Item.ButtonLayout
                },
                performance = data.optJSONObject("performance")?.let {
                    decode(it.toString()) as? Item.Performance
                },
            )
            else -> error("Unsupported control item type")
        }
    }

    private fun root(type: String, name: String, data: JSONObject): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("type", type)
        .put("name", name)
        .put("data", data)
        .toString(2)

    private fun encodeFanCurveData(name: String, profile: FanCurveProfile): JSONObject =
        JSONObject()
            .put("name", name)
            .put("points", encodePoints(profile.points))
            .put("defaultPoints", encodePoints(profile.defaultPoints))

    private fun encodeJoystickData(profile: JoystickProfile): JSONObject = JSONObject()
        .put("name", profile.name)
        .put("mode", profile.mode.name)
        .put("red", profile.red)
        .put("green", profile.green)
        .put("blue", profile.blue)
        .put("brightness", profile.brightness)

    private fun encodeButtonLayoutData(profile: ButtonLayoutProfile): JSONObject = JSONObject()
        .put("name", profile.name)
        .put("layout", profile.layout.sysfsValue)
        .put("m1", profile.m1.sysfsValue)
        .put("m2", profile.m2.sysfsValue)
        .put("triggerMode", profile.triggerMode.sysfsValue)

    private fun encodePerformanceData(
        name: String,
        profile: PerformanceProfile,
    ): JSONObject {
        val frequencies = JSONObject()
        profile.maxFrequencies.toSortedMap().forEach { (policyId, frequency) ->
            frequencies.put(policyId.toString(), frequency)
        }
        return JSONObject().put("name", name).put("maxFrequencies", frequencies)
    }

    private fun decodeFanCurveData(
        data: JSONObject,
        fallbackName: String? = null,
    ): Item.FanCurve {
        val name = data.optString("name").trim().take(40)
            .ifBlank { fallbackName?.trim()?.take(40).orEmpty() }
            .ifBlank { "Fan curve" }
        val points = decodePoints(data.getJSONArray("points"))
        val defaults = data.optJSONArray("defaultPoints")?.let(::decodePoints) ?: points
        return Item.FanCurve(name, points, defaults)
    }

    private fun decodeJoystickData(
        data: JSONObject,
        fallbackName: String? = null,
    ): Item.Joystick {
        val name = data.optString("name").trim().take(40)
            .ifBlank { fallbackName?.trim()?.take(40).orEmpty() }
            .ifBlank { "Profile" }
        return Item.Joystick(
            name = name,
            value = JoystickProfile(
                id = "imported",
                name = name,
                mode = runCatching {
                    JoystickRgbMode.valueOf(data.optString("mode"))
                }.getOrDefault(JoystickRgbMode.STATIC).takeUnless {
                    it == JoystickRgbMode.AMBILIGHT
                } ?: JoystickRgbMode.STATIC,
                red = data.optInt("red", 255),
                green = data.optInt("green", 100),
                blue = data.optInt("blue", 0),
                brightness = data.optInt("brightness", DEFAULT_JOYSTICK_BRIGHTNESS),
            ).normalized(),
        )
    }

    private fun decodePerformanceData(
        data: JSONObject,
        fallbackName: String? = null,
    ): Item.Performance {
        val name = data.optString("name").trim().take(40)
            .ifBlank { fallbackName?.trim()?.take(40).orEmpty() }
            .ifBlank { "Performance profile" }
        val encoded = data.getJSONObject("maxFrequencies")
        val frequencies = buildMap {
            encoded.keys().forEach { key ->
                val policyId = key.toIntOrNull() ?: return@forEach
                val frequency = encoded.optInt(key, 0)
                if (frequency > 0) put(policyId, frequency)
            }
        }
        require(frequencies.isNotEmpty()) { "A performance profile requires frequencies" }
        return Item.Performance(name, frequencies)
    }

    private fun decodeButtonLayoutData(
        data: JSONObject,
        fallbackName: String? = null,
    ): Item.ButtonLayout {
        val name = data.optString("name").trim().take(40)
            .ifBlank { fallbackName?.trim()?.take(40).orEmpty() }
            .ifBlank { "Button layout" }
        val layout = FaceButtonLayout.entries.firstOrNull {
            it.sysfsValue == data.optString("layout")
        } ?: error("Unsupported face button layout")
        val m1 = GamepadButtonMapping.entries.firstOrNull {
            it.sysfsValue == data.optString("m1", GamepadButtonMapping.NONE.sysfsValue)
        } ?: error("Unsupported M1 mapping")
        val m2 = GamepadButtonMapping.entries.firstOrNull {
            it.sysfsValue == data.optString("m2", GamepadButtonMapping.NONE.sysfsValue)
        } ?: error("Unsupported M2 mapping")
        val triggerMode = GamepadTriggerMode.entries.firstOrNull {
            it.sysfsValue == data.optString(
                "triggerMode",
                GamepadTriggerMode.BOTH.sysfsValue,
            )
        } ?: error("Unsupported trigger mode")
        return Item.ButtonLayout(
            name,
            ButtonLayoutProfile("imported", name, layout, m1, m2, triggerMode).normalized(),
        )
    }

    private fun encodePoints(points: List<FanCurvePoint>): JSONArray = JSONArray().also { array ->
        FanCurveSerializer.sanitize(points).forEach { point ->
            array.put(
                JSONObject()
                    .put("temperatureC", point.tempC)
                    .put("speedPercent", point.speedPercent)
            )
        }
    }

    private fun decodePoints(entries: JSONArray): List<FanCurvePoint> {
        require(entries.length() >= 2) { "A fan curve requires at least two points" }
        val raw = buildList {
            repeat(entries.length()) { index ->
                val entry = entries.getJSONObject(index)
                add(FanCurvePoint(entry.getInt("temperatureC"), entry.getInt("speedPercent")))
            }
        }
        val clean = FanCurveSerializer.sanitize(raw)
        require(clean.size == raw.size) { "Fan curve temperatures must be unique" }
        return clean
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
}
