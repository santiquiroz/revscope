package com.revscope.core.obd.escucha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineaMonitorTest {

    @Test
    fun `parsea 11 bits con espacios`() {
        val trama = LineaMonitor.parsear("7E8 03 41 0D 00", 100L, false)

        assertEquals(TramaCan(100L, 0x7E8, false, listOf(0x03, 0x41, 0x0D, 0x00)), trama)
    }

    @Test
    fun `parsea 29 bits con espacios`() {
        val trama = LineaMonitor.parsear("18 DA F1 10 03 41 0D", 50L, true)

        assertEquals(TramaCan(50L, 0x18DAF110, true, listOf(0x03, 0x41, 0x0D)), trama)
    }

    @Test
    fun `parsea 11 bits sin espacios`() {
        val trama = LineaMonitor.parsear("7E803410D00", 100L, false)

        assertEquals(TramaCan(100L, 0x7E8, false, listOf(0x03, 0x41, 0x0D, 0x00)), trama)
    }

    @Test
    fun `parsea 29 bits sin espacios`() {
        val trama = LineaMonitor.parsear("18DAF11003410D", 50L, true)

        assertEquals(TramaCan(50L, 0x18DAF110, true, listOf(0x03, 0x41, 0x0D)), trama)
    }

    @Test
    fun `acepta trama sin datos y minusculas`() {
        assertEquals(
            TramaCan(0L, 0x7E8, false, emptyList()),
            LineaMonitor.parsear("7e8", 0L, false),
        )
        assertEquals(
            TramaCan(0L, 0x7E8, false, emptyList()),
            LineaMonitor.parsear("7E8", 0L, false),
        )
    }

    @Test
    fun `lineas de estado y basura devuelven null`() {
        val lineas = listOf(
            "",
            "   ",
            ">",
            "STOPPED",
            "BUFFER FULL",
            "SEARCHING...",
            "?",
            "OK",
            "CAN ERROR",
            "HELLO",
            "ZZZ 01 02",
            "7E8 ZZ 00",
        )

        lineas.forEach { linea ->
            assertNull("esperaba null para '$linea'", LineaMonitor.parsear(linea, 0L, false))
            assertNull("esperaba null para '$linea'", LineaMonitor.parsear(linea, 0L, true))
        }
    }

    @Test
    fun `mas de 8 bytes devuelve null`() {
        assertNull(LineaMonitor.parsear("7E8 01 02 03 04 05 06 07 08 09", 0L, false))
        assertNull(LineaMonitor.parsear("7E8010203040506070809", 0L, false))
        assertNull(LineaMonitor.parsear("18 DA F1 10 01 02 03 04 05 06 07 08 09", 0L, true))
    }

    @Test
    fun `detecta desbordes y errores`() {
        assertTrue(LineaMonitor.esDesborde("BUFFER FULL"))
        assertFalse(LineaMonitor.esDesborde("7E8 01"))
        assertTrue(LineaMonitor.esError("CAN ERROR"))
        assertTrue(LineaMonitor.esError("<RX ERROR"))
        assertTrue(LineaMonitor.esError("BUS ERROR"))
        assertFalse(LineaMonitor.esError("7E8 01"))
    }
}
