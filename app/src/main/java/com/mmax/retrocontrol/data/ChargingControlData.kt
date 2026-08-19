package com.mmax.retrocontrol.data

import android.content.SharedPreferences
import androidx.core.content.edit

enum class ChargingMode {
    NORMAL,
    BYPASS,
    THRESHOLD;

    fun next(): ChargingMode = when (this) {
        NORMAL -> BYPASS
        BYPASS -> THRESHOLD
        THRESHOLD -> NORMAL
    }
}

data class ChargingControlState(
    val mode: ChargingMode = ChargingMode.NORMAL,
    val threshold: Int = ChargingControlPreferences.DEFAULT_THRESHOLD,
    val thresholdReached: Boolean = false,
)

data class ChargingDecision(
    val chargingEnabled: Boolean,
    val thresholdReached: Boolean,
)

enum class ChargingConnectionAction {
    NONE,
    RESET_TO_NORMAL,
    BEGIN_PRESERVED_SESSION,
}

object ChargingConnectionLogic {
    fun action(
        wasConnected: Boolean,
        isConnected: Boolean,
        preserveEnabled: Boolean,
    ): ChargingConnectionAction = when {
        wasConnected == isConnected -> ChargingConnectionAction.NONE
        !isConnected -> ChargingConnectionAction.RESET_TO_NORMAL
        preserveEnabled -> ChargingConnectionAction.BEGIN_PRESERVED_SESSION
        else -> ChargingConnectionAction.RESET_TO_NORMAL
    }
}

object ChargingControlLogic {
    fun decide(state: ChargingControlState, batteryLevel: Int): ChargingDecision = when (state.mode) {
        ChargingMode.NORMAL -> ChargingDecision(chargingEnabled = true, thresholdReached = false)
        ChargingMode.BYPASS -> ChargingDecision(chargingEnabled = false, thresholdReached = false)
        ChargingMode.THRESHOLD -> {
            val reached = state.thresholdReached || batteryLevel >= state.threshold
            ChargingDecision(chargingEnabled = !reached, thresholdReached = reached)
        }
    }
}

object ChargingControlPreferences {
    const val MIN_THRESHOLD = 50
    const val MAX_THRESHOLD = 100
    const val THRESHOLD_STEP = 5
    const val DEFAULT_THRESHOLD = 80

    fun load(prefs: SharedPreferences): ChargingControlState = ChargingControlState(
        mode = prefs.getString(Prefs.CHARGING_MODE, null)
            ?.let { stored -> ChargingMode.entries.firstOrNull { it.name == stored } }
            ?: ChargingMode.NORMAL,
        threshold = normalizeThreshold(
            prefs.getInt(Prefs.CHARGING_THRESHOLD, DEFAULT_THRESHOLD)
        ),
        thresholdReached = prefs.getBoolean(Prefs.CHARGING_THRESHOLD_REACHED, false),
    )

    fun setMode(prefs: SharedPreferences, mode: ChargingMode): ChargingControlState {
        prefs.edit {
            putString(Prefs.CHARGING_MODE, mode.name)
            putBoolean(Prefs.CHARGING_THRESHOLD_REACHED, false)
        }
        return load(prefs)
    }

    fun selectNext(prefs: SharedPreferences): ChargingControlState =
        setMode(prefs, load(prefs).mode.next())

    fun setThresholdAndSelect(prefs: SharedPreferences, threshold: Int): ChargingControlState {
        prefs.edit {
            putInt(Prefs.CHARGING_THRESHOLD, normalizeThreshold(threshold))
            putString(Prefs.CHARGING_MODE, ChargingMode.THRESHOLD.name)
            putBoolean(Prefs.CHARGING_THRESHOLD_REACHED, false)
        }
        return load(prefs)
    }

    fun setThresholdReached(prefs: SharedPreferences, reached: Boolean) {
        if (prefs.getBoolean(Prefs.CHARGING_THRESHOLD_REACHED, false) == reached) return
        prefs.edit { putBoolean(Prefs.CHARGING_THRESHOLD_REACHED, reached) }
    }

    fun resetSession(prefs: SharedPreferences) {
        prefs.edit {
            putString(Prefs.CHARGING_MODE, ChargingMode.NORMAL.name)
            putBoolean(Prefs.CHARGING_THRESHOLD_REACHED, false)
        }
    }

    fun beginPreservedSession(prefs: SharedPreferences): ChargingControlState =
        setMode(prefs, ChargingMode.THRESHOLD)

    fun isPreserveEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Prefs.PRESERVE_BYPASS_CHARGING, false)

    fun setPreserveEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit { putBoolean(Prefs.PRESERVE_BYPASS_CHARGING, enabled) }
    }

    fun normalizeThreshold(value: Int): Int {
        val clamped = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        return ((clamped + THRESHOLD_STEP / 2) / THRESHOLD_STEP * THRESHOLD_STEP)
            .coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }
}
