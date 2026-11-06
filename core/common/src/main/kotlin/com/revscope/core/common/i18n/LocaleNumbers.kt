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

    // Entero si redondeado a un decimal no tiene fracción (6 -> "6", 6.25 -> "6,3" en español).
    fun compact(value: Double, locale: Locale): String {
        val rounded = redondear(value, 1)
        val whole = rounded.roundToLong()
        return if (abs(rounded - whole) < 1e-9) whole.toString() else decimal(rounded, 1, locale)
    }

    private fun redondear(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (value * factor).roundToLong() / factor
    }
}
