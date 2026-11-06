package com.revscope.core.common.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun `sin preferencia y sistema en español colombiano resuelve español`() {
        assertEquals("es", AppLanguage.resolve("", Locale.forLanguageTag("es-CO")).language)
    }

    @Test
    fun `sin preferencia y sistema en alemán resuelve inglés`() {
        assertEquals("en", AppLanguage.resolve("", Locale.GERMANY).language)
    }

    @Test
    fun `preferencia explícita gana al sistema`() {
        assertEquals("en", AppLanguage.resolve("en", Locale.forLanguageTag("es-CO")).language)
    }

    @Test
    fun `tag no soportado cae a inglés`() {
        assertEquals("en", AppLanguage.resolve("fr", Locale.forLanguageTag("es-CO")).language)
    }
}
