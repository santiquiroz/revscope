package com.revscope.core.obd.taller

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

// Coma decimal fija (es-CO) sin depender de los datos de locale del sistema.
object FormatoTaller {

    fun numero(valor: Double, decimales: Int): String =
        String.format(Locale.ROOT, "%.${decimales}f", redondear(valor, decimales)).replace('.', ',')

    fun conSigno(valor: Double, decimales: Int): String {
        val texto = numero(abs(valor), decimales)
        return if (redondear(valor, decimales) < 0) "-$texto" else "+$texto"
    }

    fun compacto(valor: Double): String {
        val entero = valor.roundToLong()
        return if (abs(valor - entero) < 1e-9) entero.toString() else numero(valor, 1)
    }

    fun redondear(valor: Double, decimales: Int): Double {
        var factor = 1.0
        repeat(decimales) { factor *= 10 }
        return (valor * factor).roundToLong() / factor
    }
}
