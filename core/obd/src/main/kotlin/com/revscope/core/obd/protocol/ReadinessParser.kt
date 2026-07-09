package com.revscope.core.obd.protocol

/**
 * Parses Mode 01 PID 01 — MIL status, DTC count and I/M readiness monitors.
 * SAE J1979: A = MIL(bit7) + count(bits0-6); B = continuous monitors
 * (bits0-2 supported, bit3 = compression ignition, bits4-6 incomplete);
 * C = non-continuous supported; D = non-continuous incomplete.
 */
object ReadinessParser {

    data class MonitorResult(val nombre: String, val soportado: Boolean, val completo: Boolean)

    data class ReadinessStatus(
        val milOn: Boolean,
        val dtcCount: Int,
        val isDiesel: Boolean,
        val monitors: List<MonitorResult>,
    )

    private val CONTINUOUS = listOf("Encendido (misfire)", "Sistema de combustible", "Componentes")

    private val SPARK_MONITORS = listOf(
        "Catalizador", "Catalizador calefactado", "Sistema EVAP", "Aire secundario",
        "Refrigerante A/C", "Sensor O2", "Calefactor O2", "EGR/VVT",
    )

    private val DIESEL_MONITORS = listOf(
        "Catalizador NMHC", "Catalizador NOx", "Reservado", "Presión de sobrealimentación",
        "Reservado", "Sensor de gases", "Filtro de partículas", "EGR/VVT",
    )

    fun parse(raw: String): ReadinessStatus? {
        val bytes = ResponseParser.parsePidResponse(raw, "01") ?: return null
        if (bytes.size < 4) return null
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        val c = bytes[2].toInt() and 0xFF
        val d = bytes[3].toInt() and 0xFF

        val isDiesel = (b shr 3) and 0x01 == 1
        val monitors = buildList {
            CONTINUOUS.forEachIndexed { i, nombre ->
                add(MonitorResult(
                    nombre = nombre,
                    soportado = (b shr i) and 0x01 == 1,
                    completo = (b shr (i + 4)) and 0x01 == 0,
                ))
            }
            val names = if (isDiesel) DIESEL_MONITORS else SPARK_MONITORS
            names.forEachIndexed { i, nombre ->
                if (nombre == "Reservado") return@forEachIndexed
                add(MonitorResult(
                    nombre = nombre,
                    soportado = (c shr i) and 0x01 == 1,
                    completo = (d shr i) and 0x01 == 0,
                ))
            }
        }
        return ReadinessStatus(
            milOn = (a shr 7) and 0x01 == 1,
            dtcCount = a and 0x7F,
            isDiesel = isDiesel,
            monitors = monitors,
        )
    }
}
