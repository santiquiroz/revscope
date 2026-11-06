package com.revscope.core.obd.workshop

import com.revscope.core.obd.R
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class Mode06MidNamesTest {

    private val es = XmlStringResolver.es(R::class.java)

    @Test
    fun `names catalyst bank 1`() {
        assertEquals("Catalizador B1", Mode06MidNames.nameFor("21", es))
    }

    @Test
    fun `names catalyst bank 2`() {
        assertEquals("Catalizador B2", Mode06MidNames.nameFor("22", es))
    }

    @Test
    fun `names general misfire`() {
        assertEquals("Misfire general", Mode06MidNames.nameFor("A1", es))
    }

    @Test
    fun `names misfire per cylinder`() {
        assertEquals("Misfire cilindro 1", Mode06MidNames.nameFor("A2", es))
        assertEquals("Misfire cilindro 12", Mode06MidNames.nameFor("AD", es))
    }

    @Test
    fun `is case insensitive`() {
        assertEquals("Catalizador B1", Mode06MidNames.nameFor("21", es))
    }

    @Test
    fun `falls back to raw MID label for unknown ids`() {
        assertEquals("MID F0", Mode06MidNames.nameFor("F0", es))
    }
}
