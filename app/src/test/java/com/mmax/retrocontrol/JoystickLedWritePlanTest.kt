package com.mmax.retrocontrol

import com.mmax.retrocontrol.hardware.JoystickLedUpdate
import com.mmax.retrocontrol.hardware.buildJoystickLedWritePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoystickLedWritePlanTest {
    private val path = "/sys/class/leds/left:stick:0"

    @Test
    fun firstFrameWritesColorAndBrightness() {
        val plan = buildJoystickLedWritePlan(
            updates = mapOf(path to JoystickLedUpdate(10, 20, 30, 40)),
            previousStates = emptyMap(),
        )

        assertTrue(plan.script.contains("multi_intensity"))
        assertTrue(plan.script.contains("brightness"))
        assertEquals(JoystickLedUpdate(10, 20, 30, 40), plan.nextStates[path])
    }

    @Test
    fun identicalFrameProducesNoWrites() {
        val state = JoystickLedUpdate(10, 20, 30, 40)
        val plan = buildJoystickLedWritePlan(
            updates = mapOf(path to state),
            previousStates = mapOf(path to state),
        )

        assertTrue(plan.script.isBlank())
    }

    @Test
    fun brightnessOnlyChangeDoesNotRewriteColor() {
        val plan = buildJoystickLedWritePlan(
            updates = mapOf(path to JoystickLedUpdate(10, 20, 30, 41)),
            previousStates = mapOf(path to JoystickLedUpdate(10, 20, 30, 40)),
        )

        assertFalse(plan.script.contains("multi_intensity"))
        assertTrue(plan.script.contains("brightness"))
    }

    @Test
    fun colorOnlyChangeDoesNotRewriteBrightness() {
        val plan = buildJoystickLedWritePlan(
            updates = mapOf(path to JoystickLedUpdate(11, 20, 30, 40)),
            previousStates = mapOf(path to JoystickLedUpdate(10, 20, 30, 40)),
        )

        assertTrue(plan.script.contains("multi_intensity"))
        assertFalse(plan.script.contains("/brightness"))
    }

    @Test
    fun partialBrightnessUpdatePreservesCachedColor() {
        val plan = buildJoystickLedWritePlan(
            updates = mapOf(path to JoystickLedUpdate(brightness = 0)),
            previousStates = mapOf(path to JoystickLedUpdate(10, 20, 30, 40)),
        )

        assertEquals(JoystickLedUpdate(10, 20, 30, 0), plan.nextStates[path])
        assertFalse(plan.script.contains("multi_intensity"))
    }

    @Test
    fun forceRewritesCompleteState() {
        val state = JoystickLedUpdate(10, 20, 30, 40)
        val plan = buildJoystickLedWritePlan(
            updates = mapOf(path to state),
            previousStates = mapOf(path to state),
            force = true,
        )

        assertTrue(plan.script.contains("multi_intensity"))
        assertTrue(plan.script.contains("brightness"))
    }
}
