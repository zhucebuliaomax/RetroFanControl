package com.mmax.retrocontrol.data

import android.content.SharedPreferences
import androidx.core.content.edit

object ChargeSpeedPreferences {
    fun isSlowChargingEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Prefs.SLOW_CHARGING_ENABLED, false)

    fun setSlowChargingEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit { putBoolean(Prefs.SLOW_CHARGING_ENABLED, enabled) }
    }
}
