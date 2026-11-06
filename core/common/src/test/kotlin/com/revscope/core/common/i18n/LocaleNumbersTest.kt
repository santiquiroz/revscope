package com.revscope.core.common.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocaleNumbersTest {
    @Test
    fun `decimal en inglés usa punto`() {
        assertEquals("12.5", LocaleNumbers.decimal(12.46, 1, Locale.US))
    }

    @Test
    fun `decimal en español usa coma`() {
        assertEquals("12,5", LocaleNumbers.decimal(12.46, 1, Locale.forLanguageTag("es")))
    }

    @Test
    fun `signed negativo en español`() {
        assertEquals("-0,3", LocaleNumbers.signed(-0.3, 1, Locale.forLanguageTag("es")))
    }

    @Test
    fun `compact quita el decimal cuando es entero y si no usa la coma del idioma`() {
        assertEquals("6", LocaleNumbers.compact(6.0, Locale.forLanguageTag("es")))
        assertEquals("6,3", LocaleNumbers.compact(6.25, Locale.forLanguageTag("es")))
        assertEquals("6,0", LocaleNumbers.compact(6.04, Locale.forLanguageTag("es")))
        assertEquals("6.3", LocaleNumbers.compact(6.25, Locale.US))
    }
}
