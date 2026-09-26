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
}
