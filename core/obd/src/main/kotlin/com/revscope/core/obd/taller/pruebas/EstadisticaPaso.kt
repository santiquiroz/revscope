package com.revscope.core.obd.taller.pruebas

import kotlin.math.sqrt

// Estadística de un PID de posición en un paso: el % tal como lo da la ECU y su equivalente en voltios.
data class EstadisticaPaso(
    val clave: String,
    val n: Int,
    val hz: Double,
    val minPct: Double,
    val maxPct: Double,
    val mediaPct: Double,
    val desvPct: Double,
    val vref: ReferenciaVoltaje,
) {
    val ppPct: Double get() = maxPct - minPct
    val minV: Double get() = vref.aVoltios(minPct)
    val maxV: Double get() = vref.aVoltios(maxPct)
    val mediaV: Double get() = vref.aVoltios(mediaPct)
    val desvV: Double get() = vref.aVoltios(desvPct)
    val ppV: Double get() = vref.aVoltios(ppPct)

    companion object {
        fun de(clave: String, puntos: List<Punto>, utilMs: Long, vref: ReferenciaVoltaje): EstadisticaPaso? {
            if (puntos.isEmpty()) return null
            val valores = puntos.map { it.valor }
            val media = valores.average()
            return EstadisticaPaso(
                clave = clave,
                n = valores.size,
                hz = if (utilMs > 0) valores.size * 1_000.0 / utilMs else 0.0,
                minPct = valores.min(),
                maxPct = valores.max(),
                mediaPct = media,
                desvPct = sqrt(valores.sumOf { (it - media) * (it - media) } / valores.size),
                vref = vref,
            )
        }
    }
}
