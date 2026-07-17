package com.revscope.core.obd.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_MS = 24L * 60 * 60 * 1000

class AiRulesCacheTest {

    private val rulesJson =
        """{"cityId":"cdmx","displayName":"CDMX","rotation":{"2":[5,6]},"startHour":5,"endHour":22,
            "carDigit":"LAST","motoDigit":"LAST","validFromMs":0,"validUntilMs":1000000,
            "timeZoneId":"America/Mexico_City"}"""

    @Test
    fun `roundtrip conserva reglas y entradas NONE`() {
        val original = mapOf(
            "CDMX" to AiRulesCache.Entry(fetchedAtMs = 100L, rulesJson = rulesJson),
            "Melbourne" to AiRulesCache.Entry(fetchedAtMs = 200L, rulesJson = null),
        )
        val restored = AiRulesCache.parse(AiRulesCache.serialize(original))
        assertEquals(original, restored)
    }

    @Test
    fun `cache corrupto o vacio produce mapa vacio`() {
        assertTrue(AiRulesCache.parse(null).isEmpty())
        assertTrue(AiRulesCache.parse("").isEmpty())
        assertTrue(AiRulesCache.parse("{basura").isEmpty())
    }

    @Test
    fun `reglas son frescas hasta su validUntilMs`() {
        val entry = AiRulesCache.Entry(fetchedAtMs = 0L, rulesJson = rulesJson)
        assertTrue(AiRulesCache.isFresh(entry, nowMs = 999_999))
        assertFalse(AiRulesCache.isFresh(entry, nowMs = 1_000_001))
    }

    @Test
    fun `NONE es fresco 30 dias`() {
        val entry = AiRulesCache.Entry(fetchedAtMs = 0L, rulesJson = null)
        assertTrue(AiRulesCache.isFresh(entry, nowMs = 29 * DAY_MS))
        assertFalse(AiRulesCache.isFresh(entry, nowMs = 31 * DAY_MS))
    }

    @Test
    fun `reglas corruptas dentro de una entrada no son frescas ni parsean`() {
        val entry = AiRulesCache.Entry(fetchedAtMs = 0L, rulesJson = "{basura")
        assertFalse(AiRulesCache.isFresh(entry, nowMs = 1L))
        assertNull(AiRulesCache.rules(entry))
    }
}
