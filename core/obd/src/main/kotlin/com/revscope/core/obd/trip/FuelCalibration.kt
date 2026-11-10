package com.revscope.core.obd.trip

import kotlin.math.abs

data class Tanqueo(val timestamp: Long, val litros: Double, val lleno: Boolean)

data class ViajeCombustible(val inicio: Long, val litros: Double?, val distanciaKm: Double)

data class IntervaloTanqueo(
    val desde: Long,
    val hasta: Long,
    val litrosReales: Double,
    val litrosEstimados: Double,
    val incompleto: Boolean,
) {
    val desvio: Double get() = litrosEstimados / litrosReales - 1
}

enum class MotivoIgnorado { ANTES_DEL_FACTOR, SIN_DATOS, INCOMPLETO, DESVIO }

data class ResultadoCalibracion(
    val usados: List<IntervaloTanqueo>,
    val ignorados: List<Pair<IntervaloTanqueo, MotivoIgnorado>>,
    val factorActual: Double,
    val factorSugerido: Double?,
    val faltan: Int,
    val desvio: Double?,
)

object FuelCalibration {
    const val MIN_INTERVALOS = 3
    const val MAX_DESVIO = 0.25
    const val MIN_CAMBIO = 0.01
    const val FACTOR_MIN = 0.7
    const val FACTOR_MAX = 1.3
    const val MIN_LITROS_ESTIMADOS = 0.5
    const val DISTANCIA_MINIMA_KM = 0.5

    fun intervalos(tanqueos: List<Tanqueo>, viajes: List<ViajeCombustible>): List<IntervaloTanqueo> {
        val ordenados = tanqueos.sortedBy(Tanqueo::timestamp)
        val llenos = ordenados.filter(Tanqueo::lleno)
        return llenos.zipWithNext().map { (inicio, fin) ->
            val tanqueosIntervalo = ordenados.filter { it.timestamp > inicio.timestamp && it.timestamp <= fin.timestamp }
            val viajesIntervalo = viajes.filter { it.inicio > inicio.timestamp && it.inicio <= fin.timestamp }
            IntervaloTanqueo(
                desde = inicio.timestamp,
                hasta = fin.timestamp,
                litrosReales = tanqueosIntervalo.sumOf(Tanqueo::litros),
                litrosEstimados = viajesIntervalo.mapNotNull(ViajeCombustible::litros).sum(),
                incompleto = viajesIntervalo.any { it.litros == null && it.distanciaKm >= DISTANCIA_MINIMA_KM },
            )
        }
    }

    fun evaluar(intervalos: List<IntervaloTanqueo>, factorActual: Double, desde: Long): ResultadoCalibracion {
        val usados = mutableListOf<IntervaloTanqueo>()
        val ignorados = mutableListOf<Pair<IntervaloTanqueo, MotivoIgnorado>>()
        intervalos.forEach { intervalo ->
            motivoIgnorado(intervalo, desde)?.let { ignorados += intervalo to it } ?: run { usados += intervalo }
        }
        val reales = usados.sumOf(IntervaloTanqueo::litrosReales)
        val estimados = usados.sumOf(IntervaloTanqueo::litrosEstimados)
        val desvio = if (usados.isEmpty()) null else estimados / reales - 1
        val sugerido = sugerirFactor(usados.size, reales, estimados, factorActual)
        return ResultadoCalibracion(
            usados = usados,
            ignorados = ignorados,
            factorActual = factorActual,
            factorSugerido = sugerido,
            faltan = (MIN_INTERVALOS - usados.size).coerceAtLeast(0),
            desvio = desvio,
        )
    }

    fun parseVolumenTanqueo(texto: String, enGalones: Boolean): Double? {
        val numero = texto.trim().takeIf(String::isNotEmpty)?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val litros = if (enGalones) numero * FuelCostCalculator.LITERS_PER_GALLON else numero
        return litros.takeIf { it > 0 && it <= MAX_LITROS_TANQUEO }
    }

    private fun motivoIgnorado(intervalo: IntervaloTanqueo, desde: Long): MotivoIgnorado? = when {
        desde > intervalo.desde -> MotivoIgnorado.ANTES_DEL_FACTOR
        intervalo.litrosReales <= 0 || intervalo.litrosEstimados <= MIN_LITROS_ESTIMADOS -> MotivoIgnorado.SIN_DATOS
        intervalo.incompleto -> MotivoIgnorado.INCOMPLETO
        abs(intervalo.desvio) > MAX_DESVIO -> MotivoIgnorado.DESVIO
        else -> null
    }

    private fun sugerirFactor(usados: Int, reales: Double, estimados: Double, factorActual: Double): Double? {
        if (usados < MIN_INTERVALOS) return null
        val ratio = reales / estimados
        if (abs(ratio - 1) < MIN_CAMBIO) return null
        val candidato = (factorActual * ratio).coerceIn(FACTOR_MIN, FACTOR_MAX)
        return candidato.takeIf { abs(it - factorActual) >= MIN_CAMBIO_FACTOR }
    }

    private const val MAX_LITROS_TANQUEO = 200.0
    private const val MIN_CAMBIO_FACTOR = 0.005
}
