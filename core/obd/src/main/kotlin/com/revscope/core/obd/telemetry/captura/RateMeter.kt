package com.revscope.core.obd.telemetry.captura

import java.util.ArrayDeque

data class EstadisticasCaptura(
    val hzPorPid: Map<String, Double>,
    val peticionesPorS: Double,
    val latenciaP50Ms: Double?,
    val latenciaP95Ms: Double?,
    val ratioErrores: Double,
    val peticionesTotales: Int,
    val limitadoPor: String?,
)

/**
 * Tasa lograda, medida y no prometida: ventana deslizante por PID, peticiones/s y latencia p50/p95.
 * Los tiempos son ms monotónicos desde el inicio de la captura. Sincronizado: el poller registra y el vigilante lee.
 */
class RateMeter(private val esCan: Boolean?, private val ventanaMs: Long = VENTANA_MS) {

    private data class Peticion(val tMs: Long, val latenciaMs: Double, val falla: FallaLote?)

    private val muestras = mutableMapOf<String, ArrayDeque<Long>>()
    private val peticiones = ArrayDeque<Peticion>()
    private var totales = 0
    private var fallasTotales = 0

    @Synchronized
    fun registrarPeticion(tMs: Long, latenciaMs: Double, falla: FallaLote?) {
        peticiones.addLast(Peticion(tMs, latenciaMs, falla))
        totales++
        if (falla != null) fallasTotales++
    }

    @Synchronized
    fun registrarMuestra(pid: String, tMs: Long) {
        muestras.getOrPut(pid) { ArrayDeque() }.addLast(tMs)
    }

    @Synchronized
    fun estadisticas(ahoraMs: Long): EstadisticasCaptura {
        descartarViejas(ahoraMs)
        val spanS = ventanaEfectivaMs(ahoraMs) / 1_000.0
        val latencias = peticiones.map { it.latenciaMs }.sorted()
        val p50 = percentil(latencias, 0.50)
        return EstadisticasCaptura(
            hzPorPid = muestras.mapValues { (_, ts) -> if (spanS > 0) ts.size / spanS else 0.0 },
            peticionesPorS = if (spanS > 0) peticiones.size / spanS else 0.0,
            latenciaP50Ms = p50,
            latenciaP95Ms = percentil(latencias, 0.95),
            ratioErrores = if (totales == 0) 0.0 else fallasTotales.toDouble() / totales,
            peticionesTotales = totales,
            limitadoPor = limitadoPor(p50),
        )
    }

    private fun ventanaEfectivaMs(ahoraMs: Long): Long = minOf(ventanaMs, ahoraMs).coerceAtLeast(0)

    private fun descartarViejas(ahoraMs: Long) {
        val corte = ahoraMs - ventanaMs
        while (peticiones.isNotEmpty() && peticiones.first().tMs < corte) peticiones.removeFirst()
        muestras.values.forEach { ts -> while (ts.isNotEmpty() && ts.first() < corte) ts.removeFirst() }
    }

    private fun limitadoPor(p50: Double?): String? = when {
        peticiones.count { it.falla == FallaLote.BUFFER_FULL || it.falla == FallaLote.STOPPED } >= SATURACION_MIN ->
            "adaptador saturado"
        esCan == false -> "protocolo lento"
        p50 != null && p50 > LATENCIA_ALTA_MS -> "adaptador/ECU"
        else -> null
    }

    companion object {
        const val VENTANA_MS = 3_000L
        private const val SATURACION_MIN = 3
        private const val LATENCIA_ALTA_MS = 80.0

        fun percentil(ordenados: List<Double>, q: Double): Double? {
            if (ordenados.isEmpty()) return null
            val indice = ((ordenados.size - 1) * q).let { kotlin.math.round(it).toInt() }
            return ordenados[indice]
        }
    }
}
