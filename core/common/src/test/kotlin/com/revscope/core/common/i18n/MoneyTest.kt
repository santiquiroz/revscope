package com.revscope.core.common.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class MoneyTest {

    @Test
    fun `cada region tiene su moneda`() {
        assertEquals("COP", currencyFor(Region.CO, Locale.US).currencyCode)
        assertEquals("USD", currencyFor(Region.US, Locale("es", "CO")).currencyCode)
        assertEquals("GBP", currencyFor(Region.GB, Locale.US).currencyCode)
    }

    @Test
    fun `OTHER usa la moneda del locale del sistema`() {
        assertEquals("EUR", currencyFor(Region.OTHER, Locale.GERMANY).currencyCode)
        assertEquals("MXN", currencyFor(Region.OTHER, Locale("es", "MX")).currencyCode)
    }

    @Test
    fun `OTHER sin pais en el locale cae a USD`() {
        assertEquals("USD", currencyFor(Region.OTHER, Locale("es")).currencyCode)
    }

    @Test
    fun `COP se formatea sin decimales`() {
        val locale = Locale("es", "CO")
        val expected = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance("COP")
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }.format(12_345.4)

        val text = formatMoney(12_345.4, Currency.getInstance("COP"), locale)

        assertEquals(expected, text)
        assertFalse(text.contains(","))
    }

    @Test
    fun `GBP en Locale UK lleva libra y dos decimales`() {
        assertEquals("£1.50", formatMoney(1.5, Currency.getInstance("GBP"), Locale.UK))
    }

    @Test
    fun `USD en Locale US`() {
        assertEquals("$4.20", formatMoney(4.2, Currency.getInstance("USD"), Locale.US))
    }

    @Test
    fun `el idioma sin pais toma el pais del sistema`() {
        assertEquals(Locale("es", "CO"), moneyLocale(Locale("es"), Locale("es", "CO")))
        assertEquals(Locale.UK, moneyLocale(Locale.UK, Locale("es", "CO")))
    }

    @Test
    fun `GB y OTHER ingresan precio por litro, CO y US por galon`() {
        assertTrue(pricePerLiterInput(Region.GB))
        assertTrue(pricePerLiterInput(Region.OTHER))
        assertFalse(pricePerLiterInput(Region.CO))
        assertFalse(pricePerLiterInput(Region.US))
    }

    @Test
    fun `precio por litro se guarda por galon`() {
        assertEquals(1.5 * LITERS_PER_US_GALLON, toStoredPerGallon(1.5, Region.GB), 1e-9)
        assertEquals(16_000.0, toStoredPerGallon(16_000.0, Region.CO), 1e-9)
    }

    @Test
    fun `ida y vuelta entre precio mostrado y guardado`() {
        Region.entries.forEach { region ->
            val stored = toStoredPerGallon(1.459, region)
            assertEquals(1.459, toDisplayPrice(stored, region), 1e-9)
        }
    }
}
