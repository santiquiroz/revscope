package com.revscope.core.common.i18n

import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

object LocaleNumbers {
    fun decimal(value: Double, decimals: Int, locale: Locale): String {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val format = "%.$decimals" + "f"
        val formatted = String.format(Locale.ROOT, format, redondear(value, decimals))
        return formatted.replace('.', symbols.decimalSeparator)
    }

    fun signed(value: Double, decimals: Int, locale: Locale): String {
        val rounded = redondear(value, decimals)
        val sign = if (rounded >= 0.0) "+" else "-"
        return sign + decimal(abs(rounded), decimals, locale)
    }

    // Entero solo si el valor ya es entero; si no, un decimal (6 -> "6", 6.04 -> "6,0" en español).
    fun compact(value: Double, locale: Locale): String {
        val whole = value.roundToLong()
        return if (abs(value - whole) < 1e-9) whole.toString() else decimal(value, 1, locale)
    }

    private fun redondear(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (value * factor).roundToLong() / factor
    }
}
