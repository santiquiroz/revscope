package com.revscope.core.designsystem

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

enum class EstiloLinea { CONTINUA, DISCONTINUA, PUNTEADA }

data class PuntoGrafica(val x: Double, val y: Double)

data class SerieGrafica(val etiqueta: String, val puntos: List<PuntoGrafica>, val estilo: EstiloLinea = EstiloLinea.CONTINUA)

// Franja horizontal de valores esperados (una banda de referencia).
data class BandaGrafica(val etiqueta: String, val desde: Double, val hasta: Double)

// Tramo del eje X sombreado (un paso de una prueba), con una etiqueta corta.
data class TramoGrafica(val etiqueta: String, val desde: Double, val hasta: Double)

data class ModeloGrafica(
    val series: List<SerieGrafica>,
    val descripcion: String,
    val unidadX: String = "s",
    val bandas: List<BandaGrafica> = emptyList(),
    val tramos: List<TramoGrafica> = emptyList(),
    val rangoX: ClosedFloatingPointRange<Double>? = null,
    val rangoY: ClosedFloatingPointRange<Double>? = null,
) {
    val tieneDatos: Boolean get() = series.any { it.puntos.size >= 2 }
}

// Estilos que se reparten en orden: la serie se distingue por el trazo y la etiqueta, no solo por el color.
fun estiloDeSerie(indice: Int): EstiloLinea = EstiloLinea.entries[indice % EstiloLinea.entries.size]

internal object EscalaGrafica {

    private const val MARGEN_Y = 0.08

    fun rangoX(m: ModeloGrafica): ClosedFloatingPointRange<Double> = m.rangoX ?: amplio(
        (m.series.flatMap { s -> s.puntos.map { it.x } } + m.tramos.flatMap { listOf(it.desde, it.hasta) }),
    )

    fun rangoY(m: ModeloGrafica): ClosedFloatingPointRange<Double> {
        m.rangoY?.let { return it }
        val valores = m.series.flatMap { s -> s.puntos.map { it.y } } + m.bandas.flatMap { listOf(it.desde, it.hasta) }
        val bruto = amplio(valores)
        val margen = (bruto.endInclusive - bruto.start) * MARGEN_Y
        return (bruto.start - margen)..(bruto.endInclusive + margen)
    }

    // Marcas «redondas» (1, 2 o 5 × 10^k) dentro del rango.
    fun marcas(rango: ClosedFloatingPointRange<Double>, maximo: Int = 4): List<Double> {
        val paso = paso(rango, maximo)
        val primera = ceil(rango.start / paso) * paso
        return generateSequence(primera) { it + paso }
            .takeWhile { it <= rango.endInclusive + paso * 1e-6 }
            .map { if (abs(it) < paso * 1e-6) 0.0 else it }
            .toList()
    }

    fun paso(rango: ClosedFloatingPointRange<Double>, maximo: Int): Double {
        val bruto = (rango.endInclusive - rango.start) / maximo.coerceAtLeast(1)
        if (bruto <= 0.0) return 1.0
        val magnitud = 10.0.pow(floor(log10(bruto)))
        val normal = bruto / magnitud
        val redondo = when {
            normal <= 1.0 -> 1.0
            normal <= 2.0 -> 2.0
            normal <= 5.0 -> 5.0
            else -> 10.0
        }
        return redondo * magnitud
    }

    fun texto(valor: Double, paso: Double): String {
        val decimales = if (paso >= 1.0) 0 else ceil(-log10(paso)).toInt().coerceAtMost(3)
        return String.format(Locale.ROOT, "%.${decimales}f", valor).replace('.', ',')
    }

    private fun amplio(valores: List<Double>): ClosedFloatingPointRange<Double> {
        val min = valores.minOrNull() ?: return 0.0..1.0
        val max = valores.maxOrNull() ?: return 0.0..1.0
        if (max - min > 1e-9) return min..max
        val holgura = if (abs(min) > 1e-9) abs(min) * 0.1 else 1.0
        return (min - holgura)..(max + holgura)
    }
}
