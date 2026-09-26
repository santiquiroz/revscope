package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.PosicionEnBanda

// Cómo se cita una banda en los textos de las pruebas: rango con su unidad y de dónde sale.
object TextoBanda {

    fun citar(b: BandaReferencia, decimales: Int): String = "${rango(b, decimales)}, ${b.etiquetaOrigen}"

    fun rango(b: BandaReferencia, decimales: Int): String {
        val min = b.min
        val max = b.max
        val unidad = if (b.unidad.isBlank()) "" else " ${b.unidad}"
        return when {
            min != null && max != null -> "${n(min, decimales)}–${n(max, decimales)}$unidad"
            min != null -> "≥ ${n(min, decimales)}$unidad"
            else -> "≤ ${n(max ?: 0.0, decimales)}$unidad"
        }
    }

    fun lado(posicion: PosicionEnBanda?): String = if (posicion == PosicionEnBanda.BAJO) "por debajo" else "por encima"

    private fun n(x: Double, decimales: Int) = FormatoTaller.numero(x, decimales)
}
