package com.mmax.retrocontrol.hardware

/** The only fan curve that is allowed to run for the current device state. */
enum class ActiveFanSource { APPLICATION, USB, NONE }

/** Pure runtime decisions shared by the service and unit tests. */
object FanRuntimePolicy {
    const val OVERLAY_SAMPLE_INTERVAL_MS = 500L
    const val NORMAL_SAMPLE_INTERVAL_MS = 1_000L
    const val BELOW_START_SAMPLE_INTERVAL_MS = 3_000L
    const val USB_SAMPLE_INTERVAL_MS = 2_000L
    const val OVERLAY_RESPONSE_INTERVAL_MS = 300L
    const val NORMAL_RESPONSE_INTERVAL_MS = 500L
    const val BELOW_START_MARGIN_C = 5.0

    fun selectSource(
        applicationCurveEnabled: Boolean,
        applicationSuspendedForScreenOff: Boolean,
        usbCurveEnabled: Boolean,
        externalPowerConnected: Boolean,
    ): ActiveFanSource = when {
        applicationCurveEnabled && !applicationSuspendedForScreenOff ->
            ActiveFanSource.APPLICATION
        usbCurveEnabled && externalPowerConnected -> ActiveFanSource.USB
        else -> ActiveFanSource.NONE
    }

    fun sampleIntervalMs(
        source: ActiveFanSource,
        overlayVisible: Boolean,
        belowApplicationStart: Boolean,
    ): Long = when (source) {
        ActiveFanSource.APPLICATION -> when {
            overlayVisible -> OVERLAY_SAMPLE_INTERVAL_MS
            belowApplicationStart -> BELOW_START_SAMPLE_INTERVAL_MS
            else -> NORMAL_SAMPLE_INTERVAL_MS
        }
        ActiveFanSource.USB -> USB_SAMPLE_INTERVAL_MS
        ActiveFanSource.NONE -> Long.MAX_VALUE
    }

    fun responseIntervalMs(overlayVisible: Boolean): Long =
        if (overlayVisible) OVERLAY_RESPONSE_INTERVAL_MS else NORMAL_RESPONSE_INTERVAL_MS

    fun isSafelyBelowStart(controlTempC: Double, firstPointTempC: Int?): Boolean =
        firstPointTempC != null &&
            controlTempC > 0.0 &&
            controlTempC <= firstPointTempC - BELOW_START_MARGIN_C
}
