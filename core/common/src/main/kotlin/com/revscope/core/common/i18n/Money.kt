package com.revscope.core.common.i18n

import android.content.res.Resources
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

const val LITERS_PER_US_GALLON = 3.785411784

private val FALLBACK_CURRENCY: Currency = Currency.getInstance("USD")

fun currencyFor(region: Region, systemLocale: Locale): Currency = when (region) {
    Region.CO -> Currency.getInstance("COP")
    Region.US -> Currency.getInstance("USD")
    Region.GB -> Currency.getInstance("GBP")
    Region.OTHER -> localeCurrency(systemLocale)
}

private fun localeCurrency(locale: Locale): Currency =
    locale.country.takeIf { it.isNotEmpty() }
        ?.let { runCatching { Currency.getInstance(locale) }.getOrNull() }
        ?: FALLBACK_CURRENCY

fun formatMoney(amount: Double, currency: Currency, locale: Locale): String {
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = currency
    val digits = moneyFractionDigits(currency)
    format.minimumFractionDigits = digits
    format.maximumFractionDigits = digits
    return format.format(amount)
}

// ISO 4217 lists COP with 2 decimals, but centavos are not used in practice.
private val ZERO_DECIMAL_CODES = setOf("COP")

fun moneyFractionDigits(currency: Currency): Int =
    if (currency.currencyCode in ZERO_DECIMAL_CODES) 0 else currency.defaultFractionDigits.coerceAtLeast(0)

// Per-app language usually carries no country ("es"); the symbol and grouping come from the system country.
fun moneyLocale(uiLocale: Locale, systemLocale: Locale): Locale =
    if (uiLocale.country.isNotEmpty()) uiLocale else Locale(uiLocale.language, systemLocale.country)

fun formatRegionalMoney(amount: Double, region: Region, systemLocale: Locale, uiLocale: Locale): String =
    formatMoney(amount, currencyFor(region, systemLocale), moneyLocale(uiLocale, systemLocale))

fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]

fun pricePerLiterInput(region: Region): Boolean = region == Region.GB || region == Region.OTHER

fun toStoredPerGallon(displayPrice: Double, region: Region): Double =
    if (pricePerLiterInput(region)) displayPrice * LITERS_PER_US_GALLON else displayPrice

fun toDisplayPrice(storedPerGallon: Double, region: Region): Double =
    if (pricePerLiterInput(region)) storedPerGallon / LITERS_PER_US_GALLON else storedPerGallon
