package com.revscope.core.obd.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mode06ParserTest {

    // ── parseSupportedMids ───────────────────────────────────────────────────

    @Test
    fun `parseSupportedMids decodes bitmap same scheme as PID 00`() {
        // 80=1000_0000 (bit0 -> MID 01), 00, 00, 01=0000_0001 (bit7 -> MID 20)
        val mids = Mode06Parser.parseSupportedMids("4600 80 00 00 01")
        assertEquals(setOf("01", "20"), mids)
    }

    @Test
    fun `parseSupportedMids returns empty set for error response`() {
        assertTrue(Mode06Parser.parseSupportedMids("NO DATA").isEmpty())
    }

    @Test
    fun `parseSupportedMids returns empty set for garbage`() {
        assertTrue(Mode06Parser.parseSupportedMids("garbage").isEmpty())
    }

    @Test
    fun `parseSupportedMids returns empty set when header is not 46`() {
        assertTrue(Mode06Parser.parseSupportedMids("41 00 80 00 00 01").isEmpty())
    }

    // ── parseTestResults ──────────────────────────────────────────────────────

    @Test
    fun `parseTestResults decodes two records with pass and fail`() {
        // Record 1: MID 01, TID 01, UAS 01 (count x1), value 5 in [0,10] -> pass
        // Record 2: MID 01, TID 02, UAS 0B (kPa x1), value 20 not in [0,16] -> fail
        val raw = "46" + "01010100050000000A" + "01020B001400000010"
        val results = Mode06Parser.parseTestResults(raw)

        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("01", first.mid)
        assertEquals(1, first.tid)
        assertEquals(0x01, first.uasId)
        assertEquals(5, first.rawValue)
        assertEquals(0, first.rawMin)
        assertEquals(10, first.rawMax)
        assertEquals(5.0, first.value, 0.001)
        assertTrue(first.pass)

        val second = results[1]
        assertEquals("01", second.mid)
        assertEquals(2, second.tid)
        assertEquals(0x0B, second.uasId)
        assertEquals(20, second.rawValue)
        assertEquals(0, second.rawMin)
        assertEquals(16, second.rawMax)
        assertFalse(second.pass)
    }

    @Test
    fun `parseTestResults applies known UAS scale and unit`() {
        // MID 21, TID 01, UAS 01 (count, scale 1.0) value 42
        val raw = "46" + "210101002A00000064"
        val result = Mode06Parser.parseTestResults(raw).single()
        assertEquals("cuentas", result.unit)
        assertEquals(42.0, result.value, 0.001)
    }

    @Test
    fun `parseTestResults falls back to raw for unknown UAS id`() {
        // UAS FF is not in the known table
        val raw = "46" + "2101FF002A00000064"
        val result = Mode06Parser.parseTestResults(raw).single()
        assertEquals("raw", result.unit)
        assertEquals(42.0, result.value, 0.001)
    }

    @Test
    fun `parseTestResults returns empty list for unsupported response`() {
        assertTrue(Mode06Parser.parseTestResults("NO DATA").isEmpty())
    }

    @Test
    fun `parseTestResults returns empty list for garbage`() {
        assertTrue(Mode06Parser.parseTestResults("garbage").isEmpty())
    }

    @Test
    fun `parseTestResults returns empty list for truncated record`() {
        // Only 5 of the required 9 bytes present after the 46 header
        assertTrue(Mode06Parser.parseTestResults("46 0101010005").isEmpty())
    }
}
