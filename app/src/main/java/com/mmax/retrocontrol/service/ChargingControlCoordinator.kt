package com.mmax.retrocontrol.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mmax.retrocontrol.data.ChargingControlLogic
import com.mmax.retrocontrol.data.ChargingControlPreferences
import com.mmax.retrocontrol.hardware.BatteryConnectionReader
import com.mmax.retrocontrol.hardware.BatteryConnectionState
import com.mmax.retrocontrol.hardware.ChargingController
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ChargingControlCoordinator {
    private const val TAG = "ChargingControl"
    private val applyMutex = Mutex()

    suspend fun apply(
        context: Context,
        prefs: SharedPreferences,
        batteryOverride: BatteryConnectionState? = null,
    ): Result<Boolean> = runCatching {
        applyMutex.withLock {
            val battery = batteryOverride ?: BatteryConnectionReader.read(context)
            if (!battery.powerConnected) {
                ChargingControlPreferences.resetSession(prefs)
                return@withLock ChargingController.setChargingEnabled(true).getOrThrow()
            }

            val state = ChargingControlPreferences.load(prefs)
            val decision = ChargingControlLogic.decide(state, battery.level)
            ChargingControlPreferences.setThresholdReached(prefs, decision.thresholdReached)
            ChargingController.setChargingEnabled(decision.chargingEnabled).getOrThrow()
        }
    }.onFailure { error ->
        Log.e(TAG, "Unable to apply charging control", error)
    }
}
