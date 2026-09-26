package com.revscope.core.obd.taller.pruebas

import kotlin.math.sqrt

data class Recta(val pendientePorS: Double, val ordenada: Double) {
    fun en(tMs: Long): Double = ordenada + pendientePorS * tMs / 1_000.0
}

// Estadística simple sobre series (t en ms): la comparten los analizadores de mínimo y de arranque.
object SerieNumerica {

    fun media(valores: List<Double>): Double = valores.average()

    fun desviacion(valores: List<Double>): Double {
        val m = valores.average()
        return sqrt(valores.sumOf { (it - m) * (it - m) } / valores.size)
    }

    // Mínimos cuadrados de valor contra tiempo; sin variación de tiempo no hay recta.
    fun recta(serie: List<Punto>): Recta? {
        if (serie.size < 2) return null
        val ts = serie.map { it.tMs / 1_000.0 }
        val tMedia = ts.average()
        val vMedia = serie.map { it.valor }.average()
        val sxx = ts.sumOf { (it - tMedia) * (it - tMedia) }
        if (sxx == 0.0) return null
        val sxy = serie.indices.sumOf { (ts[it] - tMedia) * (serie[it].valor - vMedia) }
        val pendiente = sxy / sxx
        return Recta(pendiente, vMedia - pendiente * tMedia)
    }

    // Tramos seguidos de índices donde se cumple el predicado.
    fun tramos(serie: List<Punto>, dentro: (Punto) -> Boolean): List<IntRange> {
        val tramos = mutableListOf<IntRange>()
        var inicio = -1
        serie.forEachIndexed { i, p ->
            val cumple = dentro(p)
            if (cumple && inicio < 0) inicio = i
            if (!cumple && inicio >= 0) {
                tramos += inicio until i
                inicio = -1
            }
        }
        if (inicio >= 0) tramos += inicio..serie.lastIndex
        return tramos
    }

    // Cuántas veces la serie pasa de cumplir a no cumplir el predicado (p. ej. motor girando → 0 rpm).
    fun caidas(serie: List<Punto>, arriba: (Punto) -> Boolean): Int =
        serie.zipWithNext().count { (a, b) -> arriba(a) && !arriba(b) }

    fun ventana(serie: List<Punto>, desdeMs: Long, hastaMs: Long): List<Punto> = serie.filter { it.tMs in desdeMs..hastaMs }

    fun duracionMs(serie: List<Punto>, r: IntRange): Long = serie[r.last].tMs - serie[r.first].tMs
}
