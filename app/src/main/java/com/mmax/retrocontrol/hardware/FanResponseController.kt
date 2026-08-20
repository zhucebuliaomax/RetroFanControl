package com.mmax.retrocontrol.hardware

import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Filters temperature spikes, applies an output-based deadband and ramps an
 * accepted curve target over five seconds. A user-initiated curve change calls
 * [resetImmediate] and deliberately bypasses all smoothing.
 */
class FanResponseController(
    private val filterWindowMs: Long = 3_000L,
    private val outputDeadbandPercent: Double = 3.0,
    private val rampDurationMs: Long = 5_000L,
) {
    private data class Sample(val atMs: Long, val tempC: Double)

    private val samples = ArrayDeque<Sample>()
    private var acceptedTempC: Double? = null
    private var rampStartPercent = 0.0
    private var rampTargetPercent = 0.0
    private var rampStartMs = 0L

    fun resetImmediate(tempC: Double, percent: Double, nowMs: Long): Double {
        samples.clear()
        samples.addLast(Sample(nowMs, tempC))
        acceptedTempC = tempC
        rampStartPercent = percent.coerceIn(0.0, 100.0)
        rampTargetPercent = rampStartPercent
        rampStartMs = nowMs
        return rampTargetPercent
    }

    fun update(tempC: Double, nowMs: Long, curve: (Double) -> Double): Double {
        if (acceptedTempC == null) return resetImmediate(tempC, curve(tempC), nowMs)

        samples.addLast(Sample(nowMs, tempC))
        val coversWindow = samples.size >= 2 && nowMs - samples.first().atMs >= filterWindowMs
        if (coversWindow) {
            val median = samples.map { it.tempC }.sorted().let { sorted ->
                val middle = sorted.size / 2
                if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0
                else sorted[middle]
            }
            val targetAtMedian = curve(median).coerceIn(0.0, 100.0)
            if (abs(targetAtMedian - rampTargetPercent) >= outputDeadbandPercent) {
                val current = currentLevel(nowMs)
                acceptedTempC = median
                rampStartPercent = current
                rampTargetPercent = targetAtMedian
                rampStartMs = nowMs
            }
            samples.clear()
            samples.addLast(Sample(nowMs, tempC))
        }
        return currentLevel(nowMs)
    }

    fun currentLevel(nowMs: Long): Double {
        if (rampDurationMs <= 0L) return rampTargetPercent
        val progress = ((nowMs - rampStartMs).toDouble() / rampDurationMs).coerceIn(0.0, 1.0)
        return rampStartPercent + (rampTargetPercent - rampStartPercent) * progress
    }

    fun isRamping(nowMs: Long): Boolean =
        rampStartPercent != rampTargetPercent && nowMs - rampStartMs < rampDurationMs
}
