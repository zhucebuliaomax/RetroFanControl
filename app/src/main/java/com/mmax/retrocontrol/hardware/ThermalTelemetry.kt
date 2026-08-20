package com.mmax.retrocontrol.hardware

import java.io.File

enum class ThermalKind { CPU, GPU, DDR, BATTERY, USB }

data class ThermalReading(
    val zone: String,
    val name: String,
    val kind: ThermalKind,
    val tempC: Double,
)

data class TemperatureSummary(
    val averageC: Double = 0.0,
    val maxC: Double = 0.0,
    val count: Int = 0,
    val hottest: ThermalReading? = null,
)

data class ThermalSnapshot(
    val readings: List<ThermalReading> = emptyList(),
) {
    val cpu: List<ThermalReading> = readings.filter { it.kind == ThermalKind.CPU }
    val gpu: List<ThermalReading> = readings.filter { it.kind == ThermalKind.GPU }
    val ddr: ThermalReading? = readings.firstOrNull { it.kind == ThermalKind.DDR }
    val battery: ThermalReading? = readings.firstOrNull { it.kind == ThermalKind.BATTERY }
    val usb: ThermalReading? = readings.firstOrNull { it.kind == ThermalKind.USB }

    val cpuSummary: TemperatureSummary = summarize(cpu)
    val gpuSummary: TemperatureSummary = summarize(gpu)
    val computeSummary: TemperatureSummary = summarize(cpu + gpu)
    /** The fan curve follows the hotter of the CPU and GPU average temperatures. */
    val controlTempC: Double = listOf(cpuSummary, gpuSummary)
        .filter { it.count > 0 }
        .maxOfOrNull { it.averageC }
        ?: 0.0

    companion object {
        private fun summarize(items: List<ThermalReading>): TemperatureSummary =
            if (items.isEmpty()) TemperatureSummary()
            else {
                val hottest = items.maxBy { it.tempC }
                TemperatureSummary(
                    averageC = items.map { it.tempC }.average(),
                    maxC = hottest.tempC,
                    count = items.size,
                    hottest = hottest,
                )
            }
    }
}

/**
 * Dynamically discovers only CPU, GPU, DDR, battery and USB thermal zones.
 * Zone indices are deliberately never hardcoded.
 */
object ThermalSensorReader {
    private const val THERMAL_BASE = "/sys/class/thermal"

    private data class Sensor(val zone: String, val type: String, val kind: ThermalKind, val temp: File)

    @Volatile
    private var sensors: List<Sensor>? = null

    fun read(): ThermalSnapshot {
        return readSensors(discover())
    }

    /** Reads only the explicitly selected USB control sensor. */
    fun readUsb(): ThermalSnapshot {
        val usb = selectUsbSensor(discover().filter { it.kind == ThermalKind.USB })
        return readSensors(listOfNotNull(usb))
    }

    private fun readSensors(selected: List<Sensor>): ThermalSnapshot {
        val values = selected.mapNotNull { sensor ->
            val raw = runCatching { sensor.temp.readText().trim().toDouble() }.getOrNull()
                ?: return@mapNotNull null
            val celsius = normalize(raw)
            if (celsius !in 0.1..125.0) return@mapNotNull null
            ThermalReading(
                zone = sensor.zone,
                name = sensor.type,
                kind = sensor.kind,
                tempC = celsius,
            )
        }
        return ThermalSnapshot(values)
    }

    private fun selectUsbSensor(items: List<Sensor>): Sensor? =
        items.firstOrNull { it.type == "usb-therm" }
            ?: items.firstOrNull { it.type.startsWith("usb-") }
            ?: items.firstOrNull()

    internal fun selectUsbType(types: List<String>): String? {
        val normalized = types.map { it.trim().lowercase() }
        return normalized.firstOrNull { it == "usb-therm" }
            ?: normalized.firstOrNull { it.startsWith("usb-") }
            ?: normalized.firstOrNull()
    }

    internal fun normalize(raw: Double): Double =
        if (kotlin.math.abs(raw) >= 1_000.0) raw / 1_000.0 else raw

    private fun discover(): List<Sensor> {
        sensors?.let { return it }
        val discovered = File(THERMAL_BASE).listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }
            ?.mapNotNull { zone ->
                val type = runCatching { File(zone, "type").readText().trim() }.getOrNull()
                    ?.lowercase()
                    ?: return@mapNotNull null
                val kind = ThermalClassifier.classify(type)
                    ?: return@mapNotNull null
                val temp = File(zone, "temp").takeIf { it.exists() } ?: return@mapNotNull null
                Sensor(zone.name, type, kind, temp)
            }
            .orEmpty()
        sensors = discovered
        return discovered
    }

    internal fun classify(type: String): ThermalKind? =
        ThermalClassifier.classify(type)
}
