package com.revscope.core.obd.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessParserTest {

    @Test
    fun `mil encendida y conteo de dtcs`() {
        // A=0x82: MIL on, 2 DTCs. B=0x07: misfire/fuel/comp soportados, completos. C=0x00 D=0x00
        val status = ReadinessParser.parse("41 01 82 07 00 00")!!
        assertTrue(status.milOn)
        assertEquals(2, status.dtcCount)
        assertFalse(status.isDiesel)
    }

    @Test
    fun `todo listo para tecnomecanica`() {
        // A=0x00 sin MIL. B=0x07 continuos ok. C=0xE5 varios soportados, D=0x00 todos completos
        val status = ReadinessParser.parse("410100 07 E5 00")!!
        assertFalse(status.milOn)
        assertTrue(status.monitors.filter { it.soportado }.all { it.completo })
    }

    @Test
    fun `monitor soportado incompleto se reporta`() {
        // C bit0 catalizador soportado, D bit0 = incompleto
        val status = ReadinessParser.parse("41 01 00 07 01 01")!!
        val cat = status.monitors.first { it.nombre == "Catalizador" }
        assertTrue(cat.soportado)
        assertFalse(cat.completo)
    }

    @Test
    fun `motor diesel usa nombres de compresion`() {
        // B bit3 = 1 → compresión (diésel)
        val status = ReadinessParser.parse("41 01 00 0F 41 00")!!
        assertTrue(status.isDiesel)
        assertTrue(status.monitors.any { it.nombre == "Catalizador NMHC" })
    }

    @Test
    fun `respuesta invalida devuelve null`() {
        assertNull(ReadinessParser.parse("NO DATA"))
    }

    @Test
    fun `misfire incompleto en byte B`() {
        // B: bit0 misfire soportado, bit4 misfire incompleto
        val status = ReadinessParser.parse("41 01 00 11 00 00")!!
        val misfire = status.monitors.first { it.nombre == "Encendido (misfire)" }
        assertTrue(misfire.soportado)
        assertFalse(misfire.completo)
    }
}
