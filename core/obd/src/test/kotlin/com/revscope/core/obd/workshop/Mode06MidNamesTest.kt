package com.revscope.core.obd.workshop

import org.junit.Assert.assertEquals
import org.junit.Test

class Mode06MidNamesTest {

    @Test
    fun `names catalyst bank 1`() {
        assertEquals("Catalizador B1", Mode06MidNames.nameFor("21"))
    }

    @Test
    fun `names catalyst bank 2`() {
        assertEquals("Catalizador B2", Mode06MidNames.nameFor("22"))
    }

    @Test
    fun `names general misfire`() {
        assertEquals("Misfire general", Mode06MidNames.nameFor("A1"))
    }

    @Test
    fun `names misfire per cylinder`() {
        assertEquals("Misfire cilindro 1", Mode06MidNames.nameFor("A2"))
        assertEquals("Misfire cilindro 12", Mode06MidNames.nameFor("AD"))
    }

    @Test
    fun `is case insensitive`() {
        assertEquals("Catalizador B1", Mode06MidNames.nameFor("21"))
    }

    @Test
    fun `falls back to raw MID label for unknown ids`() {
        assertEquals("MID F0", Mode06MidNames.nameFor("F0"))
    }
}
