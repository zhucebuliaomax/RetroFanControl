package com.mmax.retrocontrol.hardware

import com.mmax.retrocontrol.data.FanCurvePoint
import com.mmax.retrocontrol.data.CpuFrequencyPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TelemetrySnapshot(
    val thermal: ThermalSnapshot = ThermalSnapshot(),
    val fanPercent: Int = 0,
    val fanAdjustEnabled: Boolean = false,
    val activeCurveName: String = "",
    val activeCurvePoints: List<FanCurvePoint> = emptyList(),
    val frequency: FrequencyTelemetry = FrequencyTelemetry(),
)

data class FrequencyTelemetry(
    val policies: List<CpuFrequencyPolicy> = emptyList(),
    val currentFrequenciesKhz: Map<Int, Int> = emptyMap(),
    val targetMaxFrequenciesKhz: Map<Int, Int> = emptyMap(),
    val adjustEnabled: Boolean = false,
    val activeProfileName: String = "",
)

/** One in-process source of truth shared by the dashboard, fan loop and overlay. */
object TelemetryRepository {
    private val mutable = MutableStateFlow(TelemetrySnapshot())
    val state: StateFlow<TelemetrySnapshot> = mutable.asStateFlow()

    fun updateThermal(
        thermal: ThermalSnapshot,
        fanPercent: Int,
        fanAdjustEnabled: Boolean,
        activeCurveName: String,
        activeCurvePoints: List<FanCurvePoint>,
    ) {
        mutable.update {
            it.copy(
                thermal = thermal,
                fanPercent = fanPercent.coerceIn(0, 100),
                fanAdjustEnabled = fanAdjustEnabled,
                activeCurveName = activeCurveName,
                activeCurvePoints = activeCurvePoints,
            )
        }
    }

    fun updateFrequency(
        policies: List<CpuFrequencyPolicy>,
        currentFrequenciesKhz: Map<Int, Int>,
        targetMaxFrequenciesKhz: Map<Int, Int>,
        adjustEnabled: Boolean,
        activeProfileName: String,
    ) {
        mutable.update {
            it.copy(
                frequency = FrequencyTelemetry(
                    policies = policies,
                    currentFrequenciesKhz = currentFrequenciesKhz,
                    targetMaxFrequenciesKhz = targetMaxFrequenciesKhz,
                    adjustEnabled = adjustEnabled,
                    activeProfileName = activeProfileName,
                )
            )
        }
    }
}
