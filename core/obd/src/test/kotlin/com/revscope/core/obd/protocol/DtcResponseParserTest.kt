package com.revscope.core.obd.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcResponseParserTest {

    @Test
    fun `can con un dtc descuenta el byte de conteo`() {
        val codes = DtcResponseParser.parse("43010133\r\r>", DtcServicio.ACTIVOS, esCan = true)

        assertEquals(listOf("P0133"), codes)
    }

    @Test
    fun `can sin dtc devuelve lista vacia`() {
        val codes = DtcResponseParser.parse("4300\r\r>", DtcServicio.ACTIVOS, esCan = true)

        assertTrue(codes.isEmpty())
    }

    @Test
    fun `can con dos dtc en una trama`() {
        val codes = DtcResponseParser.parse("43 02 01 33 03 00 >", DtcServicio.ACTIVOS, esCan = true)

        assertEquals(listOf("P0133", "P0300"), codes)
    }

    @Test
    fun `can multi-frame con cuatro dtc respeta el largo iso-tp`() {
        val raw = "00A\r0:430401330134\r1:013501360000AA\r\r>"

        val codes = DtcResponseParser.parse(raw, DtcServicio.ACTIVOS, esCan = true)

        assertEquals(listOf("P0133", "P0134", "P0135", "P0136"), codes)
    }

    @Test
    fun `can dos ecu sin duplicados y en orden`() {
        val raw = "43020133A123\r43010133\r>"

        val codes = DtcResponseParser.parse(raw, DtcServicio.ACTIVOS, esCan = true)

        assertEquals(listOf("P0133", "B2123"), codes)
    }

    @Test
    fun `k-line tres por trama con relleno`() {
        val codes = DtcResponseParser.parse("43 01 33 03 00 00 00 >", DtcServicio.ACTIVOS, esCan = false)

        assertEquals(listOf("P0133", "P0300"), codes)
    }

    @Test
    fun `k-line dos tramas se acumulan`() {
        val raw = "43013301340135\r43013600000000\r>"

        val codes = DtcResponseParser.parse(raw, DtcServicio.ACTIVOS, esCan = false)

        assertEquals(listOf("P0133", "P0134", "P0135", "P0136"), codes)
    }

    @Test
    fun `pendientes y permanentes usan sus prefijos 47 y 4A`() {
        assertEquals(listOf("P0420"), DtcResponseParser.parse("47010420>", DtcServicio.PENDIENTES, esCan = true))
        assertEquals(listOf("P0420"), DtcResponseParser.parse("4A010420>", DtcServicio.PERMANENTES, esCan = true))
        assertTrue(DtcResponseParser.parse("47010420>", DtcServicio.ACTIVOS, esCan = true).isEmpty())
    }

    @Test
    fun `NO DATA es lista vacia`() {
        assertTrue(DtcResponseParser.parse("NO DATA\r\r>", DtcServicio.PERMANENTES, esCan = true).isEmpty())
    }

    @Test
    fun `respuesta negativa 7F es lista vacia`() {
        assertTrue(DtcResponseParser.parse("7F0A12>", DtcServicio.PERMANENTES, esCan = true).isEmpty())
    }

    @Test
    fun `protocolo desconocido infiere can por la paridad de bytes`() {
        assertEquals(listOf("P0133"), DtcResponseParser.parse("43010133>", DtcServicio.ACTIVOS, esCan = null))
        assertEquals(listOf("P0101", "P0133"), DtcResponseParser.parse("430101 0133>", DtcServicio.ACTIVOS, esCan = null))
    }

    @Test
    fun `digitos hexadecimales en el codigo se imprimen en hex`() {
        assertEquals(listOf("P0A1F"), DtcResponseParser.parse("43010A1F>", DtcServicio.ACTIVOS, esCan = true))
    }

    @Test
    fun `el parser viejo de ResponseParser delega en el nuevo`() {
        assertEquals(listOf("P0133"), ResponseParser.parseDtcResponse("43010133>"))
    }
}
