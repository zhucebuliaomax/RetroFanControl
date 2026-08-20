package com.mmax.retrocontrol.hardware

import com.topjohnwu.superuser.Shell

data class JoystickLedState(
    val red: Int,
    val green: Int,
    val blue: Int,
    val brightness: Int,
) {
    fun normalized(): JoystickLedState = copy(
        red = red.coerceIn(0, 255),
        green = green.coerceIn(0, 255),
        blue = blue.coerceIn(0, 255),
        brightness = brightness.coerceIn(0, 255),
    )
}

internal data class JoystickLedUpdate(
    val red: Int? = null,
    val green: Int? = null,
    val blue: Int? = null,
    val brightness: Int? = null,
)

internal data class JoystickLedWritePlan(
    val script: String,
    val nextStates: Map<String, JoystickLedUpdate>,
)

internal fun buildJoystickLedWritePlan(
    updates: Map<String, JoystickLedUpdate>,
    previousStates: Map<String, JoystickLedUpdate>,
    force: Boolean = false,
): JoystickLedWritePlan {
    val nextStates = previousStates.toMutableMap()
    val script = buildString(updates.size * 64) {
        updates.forEach { (path, rawUpdate) ->
            val update = JoystickLedUpdate(
                red = rawUpdate.red?.coerceIn(0, 255),
                green = rawUpdate.green?.coerceIn(0, 255),
                blue = rawUpdate.blue?.coerceIn(0, 255),
                brightness = rawUpdate.brightness?.coerceIn(0, 255),
            )
            val previous = previousStates[path]
            val hasCompleteColor = update.red != null && update.green != null && update.blue != null
            val colorChanged = hasCompleteColor && (
                force || previous == null || previous.red != update.red ||
                    previous.green != update.green || previous.blue != update.blue
                )
            if (colorChanged) {
                append("echo \"")
                    .append(update.red).append(' ')
                    .append(update.green).append(' ')
                    .append(update.blue).append("\" > ")
                    .append(path).append("/multi_intensity || exit 1\n")
            }
            val brightnessChanged = update.brightness != null &&
                (force || previous?.brightness != update.brightness)
            if (brightnessChanged) {
                append("echo ").append(update.brightness).append(" > ")
                    .append(path).append("/brightness || exit 1\n")
            }
            nextStates[path] = JoystickLedUpdate(
                red = update.red ?: previous?.red,
                green = update.green ?: previous?.green,
                blue = update.blue ?: previous?.blue,
                brightness = update.brightness ?: previous?.brightness,
            )
        }
    }
    return JoystickLedWritePlan(script, nextStates)
}

/** Root-backed, change-suppressing writer for the eight RP6 joystick RGB LEDs. */
object JoystickRgbController {
    val ledPaths: List<String> = listOf(
        "/sys/class/leds/left:stick:0",
        "/sys/class/leds/left:stick:1",
        "/sys/class/leds/left:stick:2",
        "/sys/class/leds/left:stick:3",
        "/sys/class/leds/right:stick:0",
        "/sys/class/leds/right:stick:1",
        "/sys/class/leds/right:stick:2",
        "/sys/class/leds/right:stick:3",
    )

    private var lastStates: Map<String, JoystickLedUpdate> = emptyMap()
    private var session: Long = 0L

    @Synchronized
    fun newSession(): Long = ++session

    @Synchronized
    fun currentSession(): Long = session

    @Synchronized
    fun invalidate() {
        lastStates = emptyMap()
    }

    @Synchronized
    fun applyFrame(
        states: Map<String, JoystickLedState>,
        force: Boolean = false,
        sessionToken: Long? = null,
    ): Boolean {
        if (sessionToken != null && sessionToken != session) return false
        val updates = states.mapValues { (_, state) ->
            state.normalized().let {
                JoystickLedUpdate(it.red, it.green, it.blue, it.brightness)
            }
        }
        return applyUpdates(updates, force)
    }

    @Synchronized
    fun setAll(red: Int, green: Int, blue: Int, brightness: Int, force: Boolean = false): Boolean {
        val state = JoystickLedUpdate(red, green, blue, brightness)
        return applyUpdates(ledPaths.associateWith { state }, force)
    }

    @Synchronized
    fun setBrightness(brightness: Int, force: Boolean = false): Boolean = applyUpdates(
        ledPaths.associateWith { JoystickLedUpdate(brightness = brightness) },
        force,
    )

    fun turnOff(force: Boolean = false): Boolean = setBrightness(0, force)

    private fun applyUpdates(
        updates: Map<String, JoystickLedUpdate>,
        force: Boolean,
    ): Boolean {
        val plan = buildJoystickLedWritePlan(updates, lastStates, force)
        if (plan.script.isBlank()) return true
        val success = Shell.cmd(plan.script).exec().isSuccess
        if (success) {
            lastStates = plan.nextStates
        } else {
            lastStates = emptyMap()
        }
        return success
    }
}
