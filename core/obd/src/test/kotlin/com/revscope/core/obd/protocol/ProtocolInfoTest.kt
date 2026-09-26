package com.revscope.core.obd.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolInfoTest {

    @Test
    fun `protocolos 6 a 9 y automatico A6 son can`() {
        assertEquals(true, ProtocolInfo.esCan("6"))
        assertEquals(true, ProtocolInfo.esCan("A6"))
        assertEquals(true, ProtocolInfo.esCan("A9\r\r>"))
    }

    @Test
    fun `k-line y j1850 no son can`() {
        assertEquals(false, ProtocolInfo.esCan("3"))
        assertEquals(false, ProtocolInfo.esCan("A5"))
    }

    @Test
    fun `respuesta vacia o desconocida es null`() {
        assertNull(ProtocolInfo.esCan(null))
        assertNull(ProtocolInfo.esCan("?"))
        assertNull(ProtocolInfo.esCan("0"))
    }

    @Test
    fun `solo 6 y 8 son can de 11 bits`() {
        assertEquals(true, ProtocolInfo.esCan11Bit("6"))
        assertEquals(true, ProtocolInfo.esCan11Bit("A8"))
        assertEquals(false, ProtocolInfo.esCan11Bit("7"))
        assertEquals(false, ProtocolInfo.esCan11Bit("3"))
        assertEquals(false, ProtocolInfo.esCan11Bit(null))
    }
}
