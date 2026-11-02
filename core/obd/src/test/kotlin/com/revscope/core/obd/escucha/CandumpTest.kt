package com.revscope.core.obd.escucha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CandumpTest {

    @Test
    fun `ida y vuelta 11 bits`() {
        val trama = TramaCan(123L, 0x7E8, false, listOf(0x03, 0x41, 0x0D, 0x00))

        val texto = Candump.linea(trama, 1_730_000_000_000L)

        assertEquals("(1730000000.123000) can0 7E8#03410D00", texto)
        val leida = Candump.parsear(texto)
        assertEquals(LineaCandump(1_730_000_000_123L, 0x7E8, false, listOf(0x03, 0x41, 0x0D, 0x00)), leida)
        assertEquals(trama, Candump.aTrama(leida!!, 1_730_000_000_000L))
    }

    @Test
    fun `ida y vuelta 29 bits`() {
        val trama = TramaCan(5L, 0x18DAF110, true, listOf(0x03, 0x41))

        val texto = Candump.linea(trama, 1_730_000_000_000L)

        assertEquals("(1730000000.005000) can0 18DAF110#0341", texto)
        val leida = Candump.parsear(texto)
        assertEquals(LineaCandump(1_730_000_000_005L, 0x18DAF110, true, listOf(0x03, 0x41)), leida)
        assertEquals(trama, Candump.aTrama(leida!!, 1_730_000_000_000L))
    }

    @Test
    fun `trama sin datos queda con almohadilla sola`() {
        val texto = Candump.linea(TramaCan(0L, 0x7E8, false, emptyList()), 1_730_000_000_000L)

        assertEquals("(1730000000.000000) can0 7E8#", texto)
        assertEquals(LineaCandump(1_730_000_000_000L, 0x7E8, false, emptyList()), Candump.parsear(texto))
    }

    @Test
    fun `microsegundos reales se truncan a ms`() {
        val leida = Candump.parsear("(1730000000.123456) can0 7E8#03410D00")

        assertEquals(LineaCandump(1_730_000_000_123L, 0x7E8, false, listOf(0x03, 0x41, 0x0D, 0x00)), leida)
    }

    @Test
    fun `trama remota mas de 8 bytes y basura devuelven null`() {
        assertNull(Candump.parsear("(1730000000.123000) can0 7E8#R"))
        assertNull(Candump.parsear("(1730000000.123000) can0 7E8#010203040506070809"))
        assertNull(Candump.parsear("basura"))
        assertNull(Candump.parsear("(1730000000.123000) can0 sin-almohadilla"))
        assertNull(Candump.parsear("(1730000000.123000) can0 ZZZ#00"))
    }
}
