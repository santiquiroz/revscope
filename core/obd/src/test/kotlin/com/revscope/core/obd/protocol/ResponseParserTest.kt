package com.revscope.core.obd.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParserTest {

    // ── cleanResponse ─────────────────────────────────────────────────────────

    @Test
    fun `cleanResponse strips spaces and prompt from spaced format`() {
        val result = ResponseParser.cleanResponse("41 0C 0F A0 >")
        assertEquals("410C0FA0", result)
    }

    @Test
    fun `cleanResponse handles compact format without spaces`() {
        val result = ResponseParser.cleanResponse("410C0FA0>")
        assertEquals("410C0FA0", result)
    }

    @Test
    fun `cleanResponse strips carriage returns and line feeds`() {
        val result = ResponseParser.cleanResponse("41 0C 0F A0\r\n>")
        assertEquals("410C0FA0", result)
    }

    @Test
    fun `cleanResponse uppercases hex`() {
        val result = ResponseParser.cleanResponse("410c0fa0>")
        assertEquals("410C0FA0", result)
    }

    @Test
    fun `cleanResponse returns empty string for only prompt`() {
        val result = ResponseParser.cleanResponse(">")
        assertEquals("", result)
    }

    // ── hexToBytes ────────────────────────────────────────────────────────────

    @Test
    fun `hexToBytes converts valid hex string`() {
        val bytes = ResponseParser.hexToBytes("0FA0")
        assertArrayEquals(byteArrayOf(0x0F, 0xA0.toByte()), bytes)
    }

    @Test
    fun `hexToBytes returns null for odd length string`() {
        assertNull(ResponseParser.hexToBytes("0FA"))
    }

    @Test
    fun `hexToBytes returns null for empty string`() {
        assertNull(ResponseParser.hexToBytes(""))
    }

    @Test
    fun `hexToBytes returns null for non-hex characters`() {
        assertNull(ResponseParser.hexToBytes("ZZZZ"))
    }

    @Test
    fun `hexToBytes single byte`() {
        val bytes = ResponseParser.hexToBytes("FF")
        assertArrayEquals(byteArrayOf(0xFF.toByte()), bytes)
    }

    // ── parsePidResponse ──────────────────────────────────────────────────────

    @Test
    fun `parsePidResponse RPM from spaced format`() {
        // RPM = ((A*256)+B)/4 = ((0x0F*256)+0xA0)/4 = (3840+160)/4 = 1000 rpm
        val bytes = ResponseParser.parsePidResponse("41 0C 0F A0 >", "0C")
        assertArrayEquals(byteArrayOf(0x0F, 0xA0.toByte()), bytes)
    }

    @Test
    fun `parsePidResponse RPM from compact format`() {
        val bytes = ResponseParser.parsePidResponse("410C0FA0>", "0C")
        assertArrayEquals(byteArrayOf(0x0F, 0xA0.toByte()), bytes)
    }

    @Test
    fun `parsePidResponse speed single byte`() {
        // Speed = A = 0x59 = 89 km/h
        val bytes = ResponseParser.parsePidResponse("410D59>", "0D")
        assertArrayEquals(byteArrayOf(0x59), bytes)
    }

    @Test
    fun `parsePidResponse returns null for NO DATA`() {
        assertNull(ResponseParser.parsePidResponse("NO DATA>", "0C"))
    }

    @Test
    fun `parsePidResponse returns null for UNABLE TO CONNECT`() {
        assertNull(ResponseParser.parsePidResponse("UNABLE TO CONNECT>", "0C"))
    }

    @Test
    fun `parsePidResponse returns null for wrong PID in header`() {
        // Response is for PID 0D but we asked for 0C
        assertNull(ResponseParser.parsePidResponse("410D59>", "0C"))
    }

    @Test
    fun `parsePidResponse returns null for empty response`() {
        assertNull(ResponseParser.parsePidResponse(">", "0C"))
    }

    @Test
    fun `parsePidResponse coolant temp`() {
        // Temp = A - 40 → A = 0x7F = 127 → 127 - 40 = 87°C
        val bytes = ResponseParser.parsePidResponse("41 05 7F >", "05")
        assertArrayEquals(byteArrayOf(0x7F), bytes)
    }

    @Test
    fun `parsePidResponse MAF two bytes`() {
        // MAF = ((A*256)+B)/100 → A=0x07, B=0xD0 → (7*256+208)/100 = 20 g/s
        val bytes = ResponseParser.parsePidResponse("411007D0>", "10")
        assertArrayEquals(byteArrayOf(0x07, 0xD0.toByte()), bytes)
    }

    // ── parseSupportedPids ────────────────────────────────────────────────────

    @Test
    fun `parseSupportedPids decodes standard bitmask from 01 00`() {
        // Response "4100BE1FA813"
        // BE = 1011_1110 → PIDs 01,03,04,05,06,07
        // 1F = 0001_1111 → PIDs 0B,0C,0D,0E,0F
        // A8 = 1010_1000 → PIDs 11,13,15
        // 13 = 0001_0011 → PIDs 1C,1F,20
        val supported = ResponseParser.parseSupportedPids("4100BE1FA813>")
        assertTrue(supported.contains("01"))
        assertTrue(supported.contains("0C")) // RPM — bit in BE
        assertTrue(supported.contains("0D")) // Speed
        assertTrue(supported.contains("0F")) // IAT
        assertTrue(supported.contains("11")) // Throttle
        assertTrue(supported.contains("20")) // signals more PIDs available
    }

    @Test
    fun `parseSupportedPids returns empty set for error response`() {
        val supported = ResponseParser.parseSupportedPids("NO DATA>")
        assertTrue(supported.isEmpty())
    }

    @Test
    fun `parseSupportedPids handles extended range 01 20`() {
        // All bits set → PIDs 0x21 through 0x40
        val supported = ResponseParser.parseSupportedPids("4120FFFFFFFF>")
        assertTrue(supported.contains("21"))
        assertTrue(supported.contains("40"))
        assertFalse(supported.contains("0C")) // out of range
    }

    // ── parseDtcResponse ──────────────────────────────────────────────────────

    @Test
    fun `parseDtcResponse single P-code`() {
        // Mode 03 response "430143011" = 43 + "01 43 01 10"
        // Byte pair 01 43 → P (00) + 1 + 4 + 3 = P0143? Let's use known values
        // P0300: type=P(00), d1=0, d2=3, d3=0, d4=0 → high=0x03, low=0x00
        val dtcs = ResponseParser.parseDtcResponse("43 03 00 >")
        assertEquals(listOf("P0300"), dtcs)
    }

    @Test
    fun `parseDtcResponse multiple codes`() {
        // P0300 (0x03, 0x00) and P0301 (0x03, 0x01)
        val dtcs = ResponseParser.parseDtcResponse("43 03 00 03 01 >")
        assertEquals(listOf("P0300", "P0301"), dtcs)
    }

    @Test
    fun `parseDtcResponse returns empty for NO DATA`() {
        val dtcs = ResponseParser.parseDtcResponse("NO DATA>")
        assertTrue(dtcs.isEmpty())
    }

    @Test
    fun `parseDtcResponse returns empty for zero padding only`() {
        val dtcs = ResponseParser.parseDtcResponse("43 00 00 00 00 >")
        assertTrue(dtcs.isEmpty())
    }

    @Test
    fun `parseDtcResponse chassis code C`() {
        // C-code: bits 7-6 of high byte = 01 → C prefix
        // C0121: high = 0x41 (0100_0001), low = 0x21
        val dtcs = ResponseParser.parseDtcResponse("43 41 21 >")
        assertEquals(listOf("C0121"), dtcs)
    }

    // ── isErrorResponse ───────────────────────────────────────────────────────

    @Test
    fun `isErrorResponse true for NO DATA`() {
        assertTrue(ResponseParser.isErrorResponse("NO DATA>"))
    }

    @Test
    fun `isErrorResponse true for UNABLE TO CONNECT`() {
        assertTrue(ResponseParser.isErrorResponse("UNABLE TO CONNECT>"))
    }

    @Test
    fun `isErrorResponse true for BUFFER FULL`() {
        assertTrue(ResponseParser.isErrorResponse("BUFFER FULL>"))
    }

    @Test
    fun `isErrorResponse true for question mark`() {
        assertTrue(ResponseParser.isErrorResponse("?>"))
    }

    @Test
    fun `isErrorResponse true for empty string`() {
        assertTrue(ResponseParser.isErrorResponse(""))
    }

    @Test
    fun `isErrorResponse false for valid response`() {
        assertFalse(ResponseParser.isErrorResponse("410C0FA0>"))
    }

    @Test
    fun `isErrorResponse false for ELM version string`() {
        assertFalse(ResponseParser.isErrorResponse("ELM327 v2.1>"))
    }

    // ── isNoData / isUnableToConnect / isBufferFull ──────────────────────────

    @Test
    fun `isNoData true for spaced NO DATA`() {
        assertTrue(ResponseParser.isNoData("NO DATA>"))
    }

    @Test
    fun `isUnableToConnect true for standard message`() {
        assertTrue(ResponseParser.isUnableToConnect("UNABLE TO CONNECT>"))
    }

    @Test
    fun `isUnableToConnect true for CAN ERROR`() {
        assertTrue(ResponseParser.isUnableToConnect("CAN ERROR>"))
    }

    @Test
    fun `isBufferFull true for standard message`() {
        assertTrue(ResponseParser.isBufferFull("BUFFER FULL>"))
    }
}
