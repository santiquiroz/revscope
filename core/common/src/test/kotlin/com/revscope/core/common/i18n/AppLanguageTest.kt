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

    @Test
    fun `sin preferencia y sistema en inglés británico conserva en-GB`() {
        assertEquals("en-GB", AppLanguage.resolve("", Locale.forLanguageTag("en-GB")).toLanguageTag())
    }

    @Test
    fun `sin preferencia y sistema en español colombiano conserva es-CO`() {
        assertEquals("es-CO", AppLanguage.resolve("", Locale.forLanguageTag("es-CO")).toLanguageTag())
    }

    @Test
    fun `sin preferencia y sistema en alemán resuelve en sin país`() {
        assertEquals("en", AppLanguage.resolve("", Locale.forLanguageTag("de-DE")).toLanguageTag())
    }

    @Test
    fun `preferencia en-GB gana al sistema`() {
        assertEquals("en-GB", AppLanguage.resolve("en-GB", Locale.forLanguageTag("es-CO")).toLanguageTag())
    }

    @Test
    fun `preferencia en explícita gana a sistema en-GB`() {
        assertEquals("en", AppLanguage.resolve("en", Locale.forLanguageTag("en-GB")).toLanguageTag())
    }

    @Test
    fun `preferencia es resuelve es`() {
        assertEquals("es", AppLanguage.resolve("es", Locale.forLanguageTag("en-GB")).toLanguageTag())
    }
}
