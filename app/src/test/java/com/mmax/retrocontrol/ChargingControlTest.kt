package com.mmax.retrocontrol

import com.mmax.retrocontrol.data.ChargingControlLogic
import com.mmax.retrocontrol.data.ChargingControlPreferences
import com.mmax.retrocontrol.data.ChargingControlState
import com.mmax.retrocontrol.data.ChargingConnectionAction
import com.mmax.retrocontrol.data.ChargingConnectionLogic
import com.mmax.retrocontrol.data.ChargingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingControlTest {
    @Test
    fun powerConnectionEdges_restoreOrResetTheChargingSession() {
        assertEquals(
            ChargingConnectionAction.RESET_TO_NORMAL,
            ChargingConnectionLogic.action(
                wasConnected = true,
                isConnected = false,
                preserveEnabled = true,
            ),
        )
        assertEquals(
            ChargingConnectionAction.BEGIN_PRESERVED_SESSION,
            ChargingConnectionLogic.action(
                wasConnected = false,
                isConnected = true,
                preserveEnabled = true,
            ),
        )
        assertEquals(
            ChargingConnectionAction.RESET_TO_NORMAL,
            ChargingConnectionLogic.action(
                wasConnected = false,
                isConnected = true,
                preserveEnabled = false,
            ),
        )
        assertEquals(
            ChargingConnectionAction.NONE,
            ChargingConnectionLogic.action(
                wasConnected = true,
                isConnected = true,
                preserveEnabled = true,
            ),
        )
    }

    @Test
    fun tileModes_cycleInRequestedOrder() {
        assertEquals(ChargingMode.BYPASS, ChargingMode.NORMAL.next())
        assertEquals(ChargingMode.THRESHOLD, ChargingMode.BYPASS.next())
        assertEquals(ChargingMode.NORMAL, ChargingMode.THRESHOLD.next())
    }

    @Test
    fun normalAndImmediateBypass_requestExpectedHardwareState() {
        assertTrue(
            ChargingControlLogic.decide(
                ChargingControlState(mode = ChargingMode.NORMAL),
                batteryLevel = 80,
            ).chargingEnabled
        )
        assertFalse(
            ChargingControlLogic.decide(
                ChargingControlState(mode = ChargingMode.BYPASS),
                batteryLevel = 20,
            ).chargingEnabled
        )
    }

    @Test
    fun thresholdMode_chargesUntilTargetThenLatchesBypass() {
        val charging = ChargingControlLogic.decide(
            ChargingControlState(
                mode = ChargingMode.THRESHOLD,
                threshold = 80,
                thresholdReached = false,
            ),
            batteryLevel = 79,
        )
        assertTrue(charging.chargingEnabled)
        assertFalse(charging.thresholdReached)

        val reached = ChargingControlLogic.decide(
            ChargingControlState(
                mode = ChargingMode.THRESHOLD,
                threshold = 80,
                thresholdReached = false,
            ),
            batteryLevel = 80,
        )
        assertFalse(reached.chargingEnabled)
        assertTrue(reached.thresholdReached)

        val stillBypassed = ChargingControlLogic.decide(
            ChargingControlState(
                mode = ChargingMode.THRESHOLD,
                threshold = 80,
                thresholdReached = true,
            ),
            batteryLevel = 79,
        )
        assertFalse(stillBypassed.chargingEnabled)
        assertTrue(stillBypassed.thresholdReached)
    }

    @Test
    fun threshold_isClampedAndSnappedToFivePercentStops() {
        assertEquals(50, ChargingControlPreferences.normalizeThreshold(1))
        assertEquals(50, ChargingControlPreferences.normalizeThreshold(52))
        assertEquals(55, ChargingControlPreferences.normalizeThreshold(53))
        assertEquals(80, ChargingControlPreferences.normalizeThreshold(80))
        assertEquals(100, ChargingControlPreferences.normalizeThreshold(101))
    }
}
