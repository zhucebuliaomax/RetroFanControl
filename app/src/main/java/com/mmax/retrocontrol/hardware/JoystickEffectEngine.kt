package com.mmax.retrocontrol.hardware

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.mmax.retrocontrol.data.JoystickProfile
import com.mmax.retrocontrol.data.AmbilightPreferences
import com.mmax.retrocontrol.feature.joystick.JoystickRgbMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.abs
import kotlin.math.sin

/** Owns the currently resolved joystick effect inside RetroControl's service scope. */
class JoystickEffectEngine(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var effectJob: Job? = null
    @Volatile
    private var activeProfile: JoystickProfile? = null
    private var activeSignature: JoystickProfile? = null
    private var suspended = false
    private var projectionIntent: Intent? = null
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var ambilightThread: HandlerThread? = null
    @Volatile
    private var ambilightLeftStickLayout = AmbilightPreferences.LeftStickLayout.UPPER

    fun apply(profile: JoystickProfile?, force: Boolean = false) {
        val previousProfile = activeSignature
        activeProfile = profile
        if (
            projection != null &&
            previousProfile?.mode == JoystickRgbMode.AMBILIGHT &&
            profile?.mode == JoystickRgbMode.AMBILIGHT
        ) {
            activeSignature = profile
            if (profile.brightness != previousProfile.brightness) {
                scope.launch { JoystickRgbController.setBrightness(profile.brightness) }
            }
            return
        }
        if (!force && profile == activeSignature) return
        activeSignature = profile
        stopEffect()
        if (force) JoystickRgbController.invalidate()
        if (suspended || profile == null || profile.mode == JoystickRgbMode.OFF) {
            captureRequired = suspended && profile?.mode == JoystickRgbMode.AMBILIGHT
            scope.launch { JoystickRgbController.turnOff() }
            return
        }
        start(profile)
    }

    fun setMediaProjectionIntent(intent: Intent) {
        captureRequired = false
        projectionIntent = intent
        apply(activeProfile, force = true)
    }

    fun setAmbilightLeftStickLayout(layout: AmbilightPreferences.LeftStickLayout) {
        ambilightLeftStickLayout = layout
    }

    val hasMediaProjectionIntent: Boolean
        get() = projectionIntent != null

    fun onBatteryLevelChanged(percent: Int) {
        val profile = activeProfile
        if (!suspended && profile?.mode == JoystickRgbMode.BATTERY) {
            scope.launch {
                if (!suspended && activeProfile == profile) applyBatteryColor(profile, percent)
            }
        }
    }

    val requiresThermalSampling: Boolean
        get() = !suspended && activeProfile?.mode == JoystickRgbMode.THERMAL

    fun onThermalSnapshot(snapshot: ThermalSnapshot) {
        val profile = activeProfile
        if (!suspended && profile?.mode == JoystickRgbMode.THERMAL && snapshot.controlTempC > 0.0) {
            scope.launch {
                if (!suspended && activeProfile == profile) {
                    val (red, green, blue) = thermalColor(snapshot.controlTempC.toInt())
                    JoystickRgbController.setAll(red, green, blue, profile.brightness)
                }
            }
        }
    }

    fun suspendForScreenOff() {
        if (suspended) return
        suspended = true
        stopEffect()
        scope.launch { JoystickRgbController.turnOff() }
    }

    fun resumeAfterScreenOn() {
        if (!suspended) return
        suspended = false
        apply(activeProfile, force = true)
    }

    fun destroy() {
        stopEffect()
        captureRequired = false
        JoystickRgbController.turnOff()
    }

    private fun stopEffect() {
        JoystickRgbController.newSession()
        effectJob?.cancel()
        effectJob = null
        val consumedProjectionToken = projection != null || virtualDisplay != null
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        ambilightThread?.quitSafely()
        virtualDisplay = null
        imageReader = null
        projection = null
        ambilightThread = null
        mediaProjectionActive = false
        if (consumedProjectionToken) projectionIntent = null
    }

    private fun start(profile: JoystickProfile) {
        captureRequired = false
        when (profile.mode) {
            JoystickRgbMode.OFF -> scope.launch { JoystickRgbController.turnOff() }
            JoystickRgbMode.STATIC -> effectJob = scope.launch {
                JoystickRgbController.setAll(
                    profile.red, profile.green, profile.blue, profile.brightness,
                )
            }
            JoystickRgbMode.RAINBOW -> rotatingRainbow(profile)
            JoystickRgbMode.BREATHE -> breathe(profile)
            JoystickRgbMode.AMBILIGHT -> ambilight(profile)
            JoystickRgbMode.BATTERY -> battery(profile)
            JoystickRgbMode.THERMAL -> thermal(profile)
            JoystickRgbMode.WAVE -> wave(profile)
            JoystickRgbMode.COLOR_CYCLE -> colorCycle(profile)
            JoystickRgbMode.METEOR -> meteor(profile)
            JoystickRgbMode.FIRE -> fire(profile)
            JoystickRgbMode.AURORA -> aurora(profile)
            JoystickRgbMode.OCEAN -> ocean(profile)
            JoystickRgbMode.STARLIGHT -> starlight(profile)
        }
    }

    private fun rotatingRainbow(profile: JoystickProfile) {
        effectJob = scope.launch {
            var hue = 0f
            var nextFrameAt = SystemClock.elapsedRealtime()
            val phases = listOf(0f, 270f, 180f, 90f, 180f, 90f, 0f, 270f)
            while (isActive) {
                val states = JoystickRgbController.ledPaths.mapIndexed { index, path ->
                    val (red, green, blue) = hsvToRgb((hue + phases[index]) % 360f)
                    path to ledState(red, green, blue, profile.brightness)
                }.toMap()
                JoystickRgbController.applyFrame(states)
                hue = (hue - RAINBOW_DEGREES_PER_FRAME + 360f) % 360f
                nextFrameAt += RAINBOW_FRAME_INTERVAL_MS
                val remainingMs = nextFrameAt - SystemClock.elapsedRealtime()
                if (remainingMs > 0L) {
                    delay(remainingMs)
                } else {
                    nextFrameAt = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    private fun breathe(profile: JoystickProfile) {
        effectJob = scope.launch {
            var progress = 0.1f
            var increment = 0.05f
            while (isActive) {
                JoystickRgbController.setAll(
                    profile.red,
                    profile.green,
                    profile.blue,
                    (profile.brightness * progress).toInt(),
                )
                progress += increment
                if (progress >= 1f) {
                    progress = 1f
                    increment = -0.05f
                } else if (progress <= 0.1f) {
                    progress = 0.1f
                    increment = 0.05f
                    delay(400L)
                }
                delay(120L)
            }
        }
    }

    private fun battery(profile: JoystickProfile) {
        effectJob = scope.launch {
            val manager = context.getSystemService(android.os.BatteryManager::class.java)
            applyBatteryColor(
                profile,
                manager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY),
            )
        }
    }

    private fun applyBatteryColor(profile: JoystickProfile, percent: Int) {
        val (red, green, blue) = batteryColor(percent)
        JoystickRgbController.setAll(red, green, blue, profile.brightness)
    }

    private fun thermal(@Suppress("UNUSED_PARAMETER") profile: JoystickProfile) = Unit

    private fun wave(profile: JoystickProfile) {
        effectJob = scope.launch {
            var hue = 0f
            while (isActive) {
                val states = sequentialPaths.mapIndexed { index, path ->
                    val (red, green, blue) = hsvToRgb((hue + index * 45f) % 360f)
                    path to ledState(red, green, blue, profile.brightness)
                }.toMap()
                JoystickRgbController.applyFrame(states)
                hue = (hue + 3f) % 360f
                delay(100L)
            }
        }
    }

    private fun colorCycle(profile: JoystickProfile) {
        effectJob = scope.launch {
            var hue = 0f
            while (isActive) {
                val (red, green, blue) = hsvToRgb(hue)
                JoystickRgbController.setAll(red, green, blue, profile.brightness)
                hue = (hue + 1f) % 360f
                delay(120L)
            }
        }
    }

    private fun meteor(profile: JoystickProfile) {
        effectJob = scope.launch {
            var head = 0
            while (isActive) {
                val states = sequentialPaths.mapIndexed { index, path ->
                    val distance = (head - index + sequentialPaths.size) % sequentialPaths.size
                    val brightness = when (distance) {
                        0 -> profile.brightness
                        1 -> (profile.brightness * 0.6f).toInt()
                        2 -> (profile.brightness * 0.3f).toInt()
                        3 -> (profile.brightness * 0.1f).toInt()
                        else -> 0
                    }
                    path to ledState(profile.red, profile.green, profile.blue, brightness)
                }.toMap()
                JoystickRgbController.applyFrame(states)
                head = (head + 1) % sequentialPaths.size
                delay(120L)
            }
        }
    }

    private fun fire(profile: JoystickProfile) {
        effectJob = scope.launch {
            val random = Random()
            while (isActive) {
                val states = JoystickRgbController.ledPaths.associateWith {
                    ledState(
                        200 + random.nextInt(56),
                        random.nextInt(120),
                        random.nextInt(20),
                        (profile.brightness * (0.4f + random.nextFloat() * 0.6f)).toInt(),
                    )
                }
                JoystickRgbController.applyFrame(states)
                delay(100L + random.nextInt(60))
            }
        }
    }

    private fun aurora(profile: JoystickProfile) {
        effectJob = scope.launch {
            var time = 0f
            while (isActive) {
                val states = JoystickRgbController.ledPaths.mapIndexed { index, path ->
                    val phase = time + index * 0.8f
                    val hue = 120f + 120f * sin(phase.toDouble()).toFloat()
                    val (red, green, blue) = hsvToRgb(hue.coerceIn(0f, 359f), 0.8f)
                    val brightness = (
                        profile.brightness *
                            (0.5f + 0.5f * sin((phase * 0.7f).toDouble()).toFloat())
                        ).toInt()
                    path to ledState(red, green, blue, brightness)
                }.toMap()
                JoystickRgbController.applyFrame(states)
                time += 0.05f
                delay(100L)
            }
        }
    }

    private fun ocean(profile: JoystickProfile) {
        effectJob = scope.launch {
            var time = 0f
            while (isActive) {
                val states = JoystickRgbController.ledPaths.mapIndexed { index, path ->
                    val wave = sin((time + index * 0.9f).toDouble()).toFloat()
                    path to ledState(
                        0,
                        (80 + 80 * wave).toInt(),
                        (180 + 75 * wave).toInt(),
                        (profile.brightness * (0.3f + 0.7f * ((wave + 1f) / 2f))).toInt(),
                    )
                }.toMap()
                JoystickRgbController.applyFrame(states)
                time += 0.08f
                delay(100L)
            }
        }
    }

    private fun starlight(profile: JoystickProfile) {
        effectJob = scope.launch {
            val random = Random()
            while (isActive) {
                val states = sequentialPaths.associateWith {
                    val twinkle = random.nextFloat()
                    if (twinkle > 0.6f) {
                        val (red, green, blue) = hsvToRgb(random.nextFloat() * 360f, 0.2f)
                        ledState(red, green, blue, profile.brightness)
                    } else {
                        ledState(
                            200, 200, 255,
                            (profile.brightness * twinkle * 0.3f).toInt(),
                        )
                    }
                }
                JoystickRgbController.applyFrame(states)
                delay(100L + random.nextInt(100))
            }
        }
    }

    private fun ambilight(profile: JoystickProfile) {
        val token = projectionIntent
        if (token == null) {
            captureRequired = true
            scope.launch { JoystickRgbController.turnOff() }
            return
        }
        val ledSession = JoystickRgbController.currentSession()
        effectJob = scope.launch {
            try {
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                projection = manager.getMediaProjection(Activity.RESULT_OK, token)
                mediaProjectionActive = true
                val width = 16
                val height = 9
                val thread = HandlerThread("RetroControl Ambilight").also { it.start() }
                ambilightThread = thread
                val handler = Handler(thread.looper)
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                projection?.registerCallback(object : MediaProjection.Callback() {}, handler)
                virtualDisplay = projection?.createVirtualDisplay(
                    "RetroControl Ambilight",
                    width,
                    height,
                    160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface,
                    null,
                    null,
                )
                val smoothedColors = arrayOfNulls<Triple<Float, Float, Float>>(8)
                val previousColors = arrayOfNulls<Triple<Int, Int, Int>>(8)
                var lastFrameAt = 0L
                imageReader?.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFrameAt < AMBILIGHT_FRAME_INTERVAL_MS) {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    lastFrameAt = now
                    val plane = image.planes[0]
                    val states = mutableMapOf<String, JoystickLedState>()
                    val zones = ambilightZones(ambilightLeftStickLayout)
                    zones.forEachIndexed { index, zone ->
                        val sampledColor = averageZoneColor(plane, zone)
                        val target = enhanceLowSaturation(sampledColor)
                        val smoothed = smoothColor(smoothedColors[index], target)
                        smoothedColors[index] = smoothed
                        val smoothedTarget = Triple(
                            smoothed.first.toInt(),
                            smoothed.second.toInt(),
                            smoothed.third.toInt(),
                        )
                        val output = previousColors[index]?.let { previous ->
                            limitColorChange(
                                previous,
                                smoothedTarget,
                                AMBILIGHT_MAX_CHANNEL_STEP,
                            )
                        } ?: smoothedTarget
                        if (output != previousColors[index]) {
                            states[zone.path] = ledState(
                                output.first,
                                output.second,
                                output.third,
                                activeProfile?.brightness ?: profile.brightness,
                            )
                            previousColors[index] = output
                        }
                    }
                    image.close()
                    JoystickRgbController.applyFrame(states, sessionToken = ledSession)
                }, handler)
                while (isActive) delay(1_000L)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Ambilight effect failed", error)
                projectionIntent = null
                mediaProjectionActive = false
                captureRequired = true
            }
        }
    }

    private fun ambilightZones(
        layout: AmbilightPreferences.LeftStickLayout,
    ): List<AmbilightZone> = when (layout) {
        AmbilightPreferences.LeftStickLayout.UPPER -> AMBILIGHT_UPPER_STICK_ZONES
        AmbilightPreferences.LeftStickLayout.LOWER -> AMBILIGHT_LOWER_STICK_ZONES
    }

    private fun averageZoneColor(
        plane: android.media.Image.Plane,
        zone: AmbilightZone,
    ): Triple<Int, Int, Int> {
        var red = 0
        var green = 0
        var blue = 0
        repeat(AMBILIGHT_ZONE_SIZE) { row ->
            repeat(AMBILIGHT_ZONE_SIZE) { column ->
                val offset = (zone.y + row) * plane.rowStride +
                    (zone.x + column) * plane.pixelStride
                red += plane.buffer.get(offset).toInt() and 0xff
                green += plane.buffer.get(offset + 1).toInt() and 0xff
                blue += plane.buffer.get(offset + 2).toInt() and 0xff
            }
        }
        val sampleCount = AMBILIGHT_ZONE_SIZE * AMBILIGHT_ZONE_SIZE
        return Triple(red / sampleCount, green / sampleCount, blue / sampleCount)
    }

    private fun enhanceLowSaturation(
        color: Triple<Int, Int, Int>,
    ): Triple<Int, Int, Int> {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(color.first, color.second, color.third, hsv)
        val saturation = hsv[1]
        if (saturation <= AMBILIGHT_MIN_RELIABLE_SATURATION) return color
        val mappedSaturation = if (saturation < AMBILIGHT_SATURATION_THRESHOLD) {
            AMBILIGHT_MAPPED_MIN_SATURATION +
                saturation / AMBILIGHT_SATURATION_THRESHOLD *
                (AMBILIGHT_SATURATION_THRESHOLD - AMBILIGHT_MAPPED_MIN_SATURATION)
        } else {
            saturation
        }
        val confidence = (
            (saturation - AMBILIGHT_MIN_RELIABLE_SATURATION) /
                (AMBILIGHT_FULL_HUE_CONFIDENCE - AMBILIGHT_MIN_RELIABLE_SATURATION)
            ).coerceIn(0f, 1f)
        return hsvToRgb(
            hue = hsv[0],
            saturation = saturation + (mappedSaturation - saturation) * confidence,
            value = hsv[2],
        )
    }

    private fun smoothColor(
        previous: Triple<Float, Float, Float>?,
        target: Triple<Int, Int, Int>,
    ): Triple<Float, Float, Float> {
        if (previous == null) {
            return Triple(target.first.toFloat(), target.second.toFloat(), target.third.toFloat())
        }
        fun smooth(previousChannel: Float, targetChannel: Int): Float =
            previousChannel + (targetChannel - previousChannel) * AMBILIGHT_SMOOTHING_ALPHA
        return Triple(
            smooth(previous.first, target.first),
            smooth(previous.second, target.second),
            smooth(previous.third, target.third),
        )
    }

    private fun limitColorChange(
        previous: Triple<Int, Int, Int>,
        target: Triple<Int, Int, Int>,
        maxStep: Int,
    ): Triple<Int, Int, Int> {
        fun limit(previousChannel: Int, targetChannel: Int): Int =
            targetChannel.coerceIn(previousChannel - maxStep, previousChannel + maxStep)
        return Triple(
            limit(previous.first, target.first),
            limit(previous.second, target.second),
            limit(previous.third, target.third),
        )
    }

    private fun ledState(
        red: Int,
        green: Int,
        blue: Int,
        brightness: Int,
    ): JoystickLedState = JoystickLedState(red, green, blue, brightness).normalized()

    private fun batteryColor(percent: Int): Triple<Int, Int, Int> = when {
        percent >= 100 -> Triple(0, 255, 0)
        percent >= 50 -> lerp(
            Triple(255, 190, 0), Triple(0, 255, 0), (percent - 50) / 50f,
        )
        percent >= 0 -> lerp(Triple(255, 0, 0), Triple(255, 190, 0), percent / 50f)
        else -> Triple(255, 0, 0)
    }

    private fun thermalColor(temp: Int): Triple<Int, Int, Int> = when {
        temp <= 35 -> Triple(0, 80, 255)
        temp <= 55 -> lerp(Triple(0, 80, 255), Triple(255, 140, 0), (temp - 35) / 20f)
        temp <= 75 -> lerp(Triple(255, 140, 0), Triple(160, 0, 0), (temp - 55) / 20f)
        else -> Triple(160, 0, 0)
    }

    private fun lerp(
        first: Triple<Int, Int, Int>,
        second: Triple<Int, Int, Int>,
        fraction: Float,
    ): Triple<Int, Int, Int> {
        val amount = fraction.coerceIn(0f, 1f)
        fun channel(start: Int, end: Int) = (start + (end - start) * amount).toInt()
        return Triple(
            channel(first.first, second.first),
            channel(first.second, second.second),
            channel(first.third, second.third),
        )
    }

    private fun hsvToRgb(
        hue: Float,
        saturation: Float = 1f,
        value: Float = 1f,
    ): Triple<Int, Int, Int> {
        val normalizedHue = (hue % 360f + 360f) % 360f
        val chroma = value * saturation
        val x = chroma * (1f - abs((normalizedHue / 60f) % 2f - 1f))
        val offset = value - chroma
        val (red, green, blue) = when {
            normalizedHue < 60f -> Triple(chroma, x, 0f)
            normalizedHue < 120f -> Triple(x, chroma, 0f)
            normalizedHue < 180f -> Triple(0f, chroma, x)
            normalizedHue < 240f -> Triple(0f, x, chroma)
            normalizedHue < 300f -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        return Triple(
            ((red + offset) * 255).toInt().coerceIn(0, 255),
            ((green + offset) * 255).toInt().coerceIn(0, 255),
            ((blue + offset) * 255).toInt().coerceIn(0, 255),
        )
    }

    companion object {
        private const val TAG = "JoystickEffectEngine"
        private const val RAINBOW_FRAME_INTERVAL_MS = 83L
        private const val AMBILIGHT_FRAME_INTERVAL_MS = 83L
        private const val RAINBOW_DEGREES_PER_FRAME = 4f
        private const val AMBILIGHT_ZONE_SIZE = 2
        private const val AMBILIGHT_MAX_CHANNEL_STEP = 17
        private const val AMBILIGHT_SMOOTHING_ALPHA = 0.2f
        private const val AMBILIGHT_MIN_RELIABLE_SATURATION = 0.02f
        private const val AMBILIGHT_FULL_HUE_CONFIDENCE = 0.08f
        private const val AMBILIGHT_MAPPED_MIN_SATURATION = 0.25f
        private const val AMBILIGHT_SATURATION_THRESHOLD = 0.75f
        @Volatile
        var mediaProjectionActive = false
            private set

        @Volatile
        var captureRequired = false
            private set
        private data class AmbilightZone(val path: String, val x: Int, val y: Int)
        private val AMBILIGHT_UPPER_STICK_ZONES = listOf(
            AmbilightZone("/sys/class/leds/left:stick:0", 1, 1),
            AmbilightZone("/sys/class/leds/left:stick:3", 4, 1),
            AmbilightZone("/sys/class/leds/left:stick:1", 1, 4),
            AmbilightZone("/sys/class/leds/left:stick:2", 4, 4),
            AmbilightZone("/sys/class/leds/right:stick:2", 10, 3),
            AmbilightZone("/sys/class/leds/right:stick:1", 13, 3),
            AmbilightZone("/sys/class/leds/right:stick:3", 10, 6),
            AmbilightZone("/sys/class/leds/right:stick:0", 13, 6),
        )
        private val AMBILIGHT_LOWER_STICK_ZONES = listOf(
            AmbilightZone("/sys/class/leds/left:stick:0", 1, 3),
            AmbilightZone("/sys/class/leds/left:stick:3", 4, 3),
            AmbilightZone("/sys/class/leds/left:stick:1", 1, 6),
            AmbilightZone("/sys/class/leds/left:stick:2", 4, 6),
            AmbilightZone("/sys/class/leds/right:stick:2", 10, 3),
            AmbilightZone("/sys/class/leds/right:stick:1", 13, 3),
            AmbilightZone("/sys/class/leds/right:stick:3", 10, 6),
            AmbilightZone("/sys/class/leds/right:stick:0", 13, 6),
        )
        private val sequentialPaths = listOf(
            "/sys/class/leds/left:stick:0",
            "/sys/class/leds/left:stick:3",
            "/sys/class/leds/right:stick:2",
            "/sys/class/leds/right:stick:1",
            "/sys/class/leds/right:stick:0",
            "/sys/class/leds/right:stick:3",
            "/sys/class/leds/left:stick:2",
            "/sys/class/leds/left:stick:1",
        )
    }
}
