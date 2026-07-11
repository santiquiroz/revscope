package com.revscope.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleDiscoveryTest {

    @Test
    fun `header 11-bit valido es exactamente 3 hex`() {
        assertTrue(ModuleDiscovery.isValid11BitHeader("7E0"))
        assertTrue(ModuleDiscovery.isValid11BitHeader("720"))
        assertTrue(ModuleDiscovery.isValid11BitHeader("7df"))
    }

    @Test
    fun `header invalido rechazado`() {
        assertFalse(ModuleDiscovery.isValid11BitHeader(""))
        assertFalse(ModuleDiscovery.isValid11BitHeader("7DF0"))
        assertFalse(ModuleDiscovery.isValid11BitHeader("GG1"))
        assertFalse(ModuleDiscovery.isValid11BitHeader("7E"))
    }

    @Test
    fun `solo protocolos CAN 11-bit soportados`() {
        assertTrue(ModuleDiscovery.isCan11Bit("6"))
        assertTrue(ModuleDiscovery.isCan11Bit("8"))
        assertTrue(ModuleDiscovery.isCan11Bit("A6")) // auto encontró 6
        assertTrue(ModuleDiscovery.isCan11Bit("A8"))
    }

    @Test
    fun `protocolos 29-bit y no-CAN y null no soportados`() {
        assertFalse(ModuleDiscovery.isCan11Bit("7"))
        assertFalse(ModuleDiscovery.isCan11Bit("9"))
        assertFalse(ModuleDiscovery.isCan11Bit("A7"))
        assertFalse(ModuleDiscovery.isCan11Bit("3"))
        assertFalse(ModuleDiscovery.isCan11Bit(null))
        assertFalse(ModuleDiscovery.isCan11Bit(""))
    }

    @Test
    fun `respuesta positiva marca modulo presente y extrae reply header`() {
        // H1 activo: cleanResponse no quita el header CAN, solo espacios y CR/LF/prompt.
        val r = ModuleDiscovery.interpretProbe("7E0", "7E8 06 62 F1 90 12 34 56 \r>")
        assertTrue(r.present)
        assertEquals("7E8", r.replyHeader)
    }

    @Test
    fun `respuesta negativa 7F prueba que el modulo existe`() {
        // El módulo contestó rechazando el DID (7F 22 31 = requestOutOfRange) → PRESENTE.
        // "7F" no está en ERROR_TOKENS de ResponseParser, así que no cuenta como error.
        val r = ModuleDiscovery.interpretProbe("720", "728 03 7F 22 31 \r>")
        assertTrue(r.present)
        assertEquals("728", r.replyHeader)
    }

    @Test
    fun `NO DATA marca modulo ausente`() {
        val r = ModuleDiscovery.interpretProbe("7A0", "NO DATA\r>")
        assertFalse(r.present)
        assertNull(r.replyHeader)
    }

    @Test
    fun `respuesta vacia marca ausente`() {
        val r = ModuleDiscovery.interpretProbe("7C0", "\r>")
        assertFalse(r.present)
        assertNull(r.replyHeader)
    }
}
