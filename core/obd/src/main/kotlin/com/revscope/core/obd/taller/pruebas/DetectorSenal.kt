package com.revscope.core.obd.taller.pruebas

import kotlin.math.abs
import kotlin.math.sign

enum class TipoIrregularidad { SALTO, CORTE, ZONA_MUERTA }

data class Irregularidad(val tipo: TipoIrregularidad, val tMs: Long, val valorV: Double, val duracionMs: Long = 0)

// Detecciones sobre una serie en voltios (§2.4.2 del diseño del Taller); umbrales típicos, no del fabricante.
object DetectorSenal {

    const val SALTO_MIN_V = 0.5
    const val SALTO_MAX_SEPARACION_MS = 100L
    const val CORTE_V = 0.1
    const val CORTE_MARGEN_BAJO_CERRADO_V = 0.05
    const val PLANO_V = 0.01
    const val ZONA_MUERTA_MIN_MS = 300L
    const val ZONA_MUERTA_DESDE = 0.15
    const val ZONA_MUERTA_HASTA = 0.85

    fun saltos(serie: List<Punto>): List<Irregularidad> = serie.zipWithNext()
        .filter { (a, b) -> b.tMs - a.tMs <= SALTO_MAX_SEPARACION_MS && abs(b.valor - a.valor) > SALTO_MIN_V }
        .map { (a, b) -> Irregularidad(TipoIrregularidad.SALTO, b.tMs, b.valor, b.tMs - a.tMs) }

    // Una caída claramente por debajo del cerrado medido; cada tramo seguido cuenta como un corte.
    fun umbralCorte(cerradoV: Double): Double = minOf(CORTE_V, cerradoV - CORTE_MARGEN_BAJO_CERRADO_V)

    fun cortes(serie: List<Punto>, umbralV: Double): List<Irregularidad> =
        tramos(serie) { _, p -> p.valor < umbralV }.map { r -> irregularidad(TipoIrregularidad.CORTE, serie, r) }

    fun zonasMuertas(serie: List<Punto>, cerradoV: Double, fondoV: Double): List<Irregularidad> {
        if (fondoV <= cerradoV) return emptyList()
        return tramos(serie) { i, p -> i > 0 && abs(p.valor - serie[i - 1].valor) < PLANO_V }
            .map { (it.first - 1)..it.last }
            .filter { esZonaMuerta(serie, it, cerradoV, fondoV) }
            .map { r -> irregularidad(TipoIrregularidad.ZONA_MUERTA, serie, r) }
    }

    // Plana, larga, en la parte media del recorrido y con la serie subiendo (o bajando) a ambos lados.
    private fun esZonaMuerta(serie: List<Punto>, r: IntRange, cerradoV: Double, fondoV: Double): Boolean {
        if (r.first == 0 || r.last == serie.lastIndex) return false
        if (serie[r.last].tMs - serie[r.first].tMs <= ZONA_MUERTA_MIN_MS) return false
        val posicion = (serie[r.first].valor - cerradoV) / (fondoV - cerradoV)
        if (posicion !in ZONA_MUERTA_DESDE..ZONA_MUERTA_HASTA) return false
        val antes = sign(serie[r.first].valor - serie[r.first - 1].valor)
        val despues = sign(serie[r.last + 1].valor - serie[r.last].valor)
        return antes != 0.0 && antes == despues
    }

    private fun tramos(serie: List<Punto>, dentro: (Int, Punto) -> Boolean): List<IntRange> {
        val tramos = mutableListOf<IntRange>()
        var inicio = -1
        serie.forEachIndexed { i, p ->
            if (dentro(i, p) && inicio < 0) inicio = i
            if (!dentro(i, p) && inicio >= 0) {
                tramos += inicio until i
                inicio = -1
            }
        }
        if (inicio >= 0) tramos += inicio..serie.lastIndex
        return tramos
    }

    private fun irregularidad(tipo: TipoIrregularidad, serie: List<Punto>, r: IntRange) =
        Irregularidad(tipo, serie[r.first].tMs, serie[r.first].valor, serie[r.last].tMs - serie[r.first].tMs)
}
