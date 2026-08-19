package com.mmax.retrocontrol.data

import android.content.SharedPreferences
import androidx.core.content.edit

data class UsbThermalFanControl(
    val enabled: Boolean = true,
    val profile: FanCurveProfile = factoryProfile(),
) {
    companion object {
        const val PROFILE_ID = "usb-thermal"

        val factoryPoints = listOf(
            FanCurvePoint(tempC = 45, speedPercent = 20),
            FanCurvePoint(tempC = 60, speedPercent = 20),
            FanCurvePoint(tempC = 80, speedPercent = 40),
        )

        fun factoryProfile() = FanCurveProfile(
            id = PROFILE_ID,
            points = factoryPoints,
            defaultPoints = factoryPoints,
        )
    }
}

object UsbThermalFanCurvePreferences {
    fun load(prefs: SharedPreferences): UsbThermalFanControl {
        val factory = UsbThermalFanControl.factoryPoints
        val points = FanCurveSerializer.parse(
            prefs.getString(Prefs.USB_THERMAL_FAN_CURVE, null),
            factory,
        )
        val defaults = FanCurveSerializer.parse(
            prefs.getString(Prefs.USB_THERMAL_FAN_CURVE_DEFAULT, null),
            factory,
        )
        return UsbThermalFanControl(
            enabled = prefs.getBoolean(Prefs.USB_THERMAL_CONTROL_ENABLED, true),
            profile = UsbThermalFanControl.factoryProfile().copy(
                points = points,
                defaultPoints = defaults,
            ),
        )
    }

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean): UsbThermalFanControl {
        prefs.edit { putBoolean(Prefs.USB_THERMAL_CONTROL_ENABLED, enabled) }
        return load(prefs)
    }

    fun savePoints(
        prefs: SharedPreferences,
        points: List<FanCurvePoint>,
    ): UsbThermalFanControl = update(prefs) { it.withPoints(points) }

    fun setCurrentAsDefault(
        prefs: SharedPreferences,
        points: List<FanCurvePoint>,
    ): UsbThermalFanControl = update(prefs) { it.withCurrentAsDefault(points) }

    fun reset(prefs: SharedPreferences): UsbThermalFanControl =
        update(prefs, FanCurveProfile::reset)

    fun replace(
        prefs: SharedPreferences,
        enabled: Boolean,
        points: List<FanCurvePoint>,
        defaultPoints: List<FanCurvePoint>,
    ): UsbThermalFanControl {
        val profile = UsbThermalFanControl.factoryProfile()
            .copy(points = points, defaultPoints = defaultPoints)
        persist(prefs, enabled, profile)
        return UsbThermalFanControl(enabled, profile)
    }

    private fun update(
        prefs: SharedPreferences,
        transform: (FanCurveProfile) -> FanCurveProfile,
    ): UsbThermalFanControl {
        val current = load(prefs)
        val profile = transform(current.profile)
        persist(prefs, current.enabled, profile)
        return current.copy(profile = profile)
    }

    private fun persist(
        prefs: SharedPreferences,
        enabled: Boolean,
        profile: FanCurveProfile,
    ) {
        prefs.edit {
            putBoolean(Prefs.USB_THERMAL_CONTROL_ENABLED, enabled)
            putString(
                Prefs.USB_THERMAL_FAN_CURVE,
                FanCurveSerializer.serialize(profile.points),
            )
            putString(
                Prefs.USB_THERMAL_FAN_CURVE_DEFAULT,
                FanCurveSerializer.serialize(profile.defaultPoints),
            )
        }
    }
}
