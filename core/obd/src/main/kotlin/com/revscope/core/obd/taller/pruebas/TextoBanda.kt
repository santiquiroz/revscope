package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.PosicionEnBanda

// Cómo se cita una banda en los textos de las pruebas: rango con su unidad y de dónde sale. La unidad va con
// espacio duro: con letra grande no queda sola en el renglón siguiente.
object TextoBanda {

    fun citar(b: BandaReferencia, decimales: Int): String = "${rango(b, decimales)}, ${b.etiquetaOrigen}"

    fun citar(b: BandaReferencia, decimales: Int, resolver: StringResolver): String =
        "${rango(b, decimales, resolver)}, ${b.etiquetaOrigen(resolver)}"

    fun rango(b: BandaReferencia, decimales: Int): String {
        val unidad = if (b.unidad.isBlank()) "" else " ${unidad(b.unidad)}"
        return when {
            b.min != null && b.max != null -> "${FormatoTaller.numero(b.min, decimales)}–${FormatoTaller.numero(b.max, decimales)}$unidad"
            b.min != null -> "≥ ${FormatoTaller.numero(b.min, decimales)}$unidad"
            else -> "≤ ${FormatoTaller.numero(b.max ?: 0.0, decimales)}$unidad"
        }
    }

    fun rango(b: BandaReferencia, decimales: Int, resolver: StringResolver): String {
        val min = b.min
        val max = b.max
        val unidad = if (b.unidad.isBlank()) "" else " ${unidad(b.unidad)}"
        return when {
            min != null && max != null -> "${n(min, decimales, resolver)}–${n(max, decimales, resolver)}$unidad"
            min != null -> "≥ ${n(min, decimales, resolver)}$unidad"
            else -> "≤ ${n(max ?: 0.0, decimales, resolver)}$unidad"
        }
    }

    // «rpm/s» no se parte después de la barra (U+2060 une sin ocupar espacio).
    fun unidad(u: String): String = u.replace("/", "/$UNIR")

    fun lado(posicion: PosicionEnBanda?): String = if (posicion == PosicionEnBanda.BAJO) "por debajo" else "por encima"

    private const val UNIR = "\u2060"

    private fun n(x: Double, decimales: Int, resolver: StringResolver) =
        LocaleNumbers.decimal(x, decimales, resolver.locale)
}
