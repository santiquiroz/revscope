package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.PosicionEnBanda

// Cómo se cita una banda en los textos de las pruebas: rango con su unidad y de dónde sale. La unidad va con
// espacio duro: con letra grande no queda sola en el renglón siguiente. Sin resolutor, las cifras salen con la
// coma de FormatoTaller (quienes todavía no lo reciben).
object TextoBanda {

    fun citar(b: BandaReferencia, decimales: Int, resolver: StringResolver? = null): String =
        "${rango(b, decimales, resolver)}, ${b.etiquetaOrigen}"

    fun rango(b: BandaReferencia, decimales: Int, resolver: StringResolver? = null): String {
        val min = b.min
        val max = b.max
        val unidad = if (b.unidad.isBlank()) "" else ESPACIO_DURO + unidad(b.unidad)
        return when {
            min != null && max != null -> "${n(min, decimales, resolver)}–${n(max, decimales, resolver)}$unidad"
            min != null -> "≥$ESPACIO_DURO${n(min, decimales, resolver)}$unidad"
            else -> "≤$ESPACIO_DURO${n(max ?: 0.0, decimales, resolver)}$unidad"
        }
    }

    // «rpm/s» no se parte después de la barra (U+2060 une sin ocupar espacio).
    fun unidad(u: String): String = u.replace("/", "/$UNIR")

    fun lado(posicion: PosicionEnBanda?, resolver: StringResolver): String =
        resolver.get(if (posicion == PosicionEnBanda.BAJO) R.string.obd_banda_por_debajo else R.string.obd_banda_por_encima)

    private const val UNIR = "⁠"
    private const val ESPACIO_DURO = " "

    private fun n(x: Double, decimales: Int, resolver: StringResolver?) =
        resolver?.let { LocaleNumbers.decimal(x, decimales, it.locale) } ?: FormatoTaller.numero(x, decimales)
}
