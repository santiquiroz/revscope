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
}
