package com.mmax.retrocontrol.hardware

/**
 * Classifies the semantic names exported by each thermal zone's `type` file.
 *
 * Thermal-zone indices are unstable across devices and boots, while vendors
 * normally retain the component name in `type` (for example cpu-1-0, cpuss-0,
 * gpuss-0, ddr or battery). Unknown zones are deliberately ignored: choosing
 * the hottest arbitrary zone could mistake a charger or PMIC sensor for the
 * SoC control temperature. USB is classified for display only and never feeds
 * the fan control temperature.
 */
internal object ThermalClassifier {
    fun classify(type: String): ThermalKind? {
        val normalized = type.trim().lowercase()
        return when {
            "battery" in normalized -> ThermalKind.BATTERY
            "gpu" in normalized -> ThermalKind.GPU
            "cpu" in normalized -> ThermalKind.CPU
            "ddr" in normalized || "dram" in normalized -> ThermalKind.DDR
            "usb" in normalized -> ThermalKind.USB
            else -> null
        }
    }
}
