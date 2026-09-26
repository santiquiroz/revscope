package com.revscope.core.obd.taller.pruebas

import kotlin.math.abs

data class Escalon(val tMs: Long, val delta: Double, val duracionMs: Long)

// Cambios de más de [minDelta] en menos de [maxMs]: se buscan por ventana (la ECU entrega la temperatura en
// escalones de 1 °C, así que un salto puede quedar repartido en varias muestras) y se informan con el tramo
// más corto que explica el cambio.
object DetectorEscalones {

    private const val HOLGURA = 0.5

    private data class Par(val j: Int, val i: Int)

    fun detectar(serie: List<Punto>, minDelta: Double, maxMs: Long): List<Escalon> =
        regiones(pares(serie, minDelta, maxMs)).map { region -> escalon(serie, region) }

    private fun pares(serie: List<Punto>, minDelta: Double, maxMs: Long): List<Par> = serie.indices.flatMap { i ->
        (i - 1 downTo 0)
            .takeWhile { j -> serie[i].tMs - serie[j].tMs < maxMs }
            .filter { j -> abs(serie[i].valor - serie[j].valor) > minDelta }
            .map { j -> Par(j, i) }
    }

    // Pares que se solapan son el mismo salto.
    private fun regiones(pares: List<Par>): List<List<Par>> {
        val regiones = mutableListOf<MutableList<Par>>()
        pares.sortedBy { it.j }.forEach { par ->
            val ultima = regiones.lastOrNull()
            if (ultima != null && par.j <= ultima.maxOf { it.i }) ultima += par else regiones += mutableListOf(par)
        }
        return regiones
    }

    private fun escalon(serie: List<Punto>, region: List<Par>): Escalon {
        val mayor = region.maxBy { abs(delta(serie, it)) }
        val ajustado = acortar(serie, mayor, abs(delta(serie, mayor)) - HOLGURA)
        return Escalon(serie[ajustado.j].tMs, delta(serie, ajustado), serie[ajustado.i].tMs - serie[ajustado.j].tMs)
    }

    private fun acortar(serie: List<Punto>, par: Par, minimo: Double): Par {
        var j = par.j
        var i = par.i
        while (j + 1 < i && abs(serie[i].valor - serie[j + 1].valor) >= minimo) j++
        while (i - 1 > j && abs(serie[i - 1].valor - serie[j].valor) >= minimo) i--
        return Par(j, i)
    }

    private fun delta(serie: List<Punto>, par: Par): Double = serie[par.i].valor - serie[par.j].valor
}
