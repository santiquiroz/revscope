package com.revscope.core.common.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.UnknownServiceException

class NetworkErrorExplainerTest {

    private val cleartextMessage =
        "Android bloquea http:// fuera de este teléfono: usa https (p. ej. un proxy con certificado) o un túnel"

    @Test
    fun `UnknownServiceException de cleartext se explica`() {
        val error = UnknownServiceException(
            "CLEARTEXT communication to 192.168.1.20 not permitted by network security policy",
        )

        assertEquals(cleartextMessage, NetworkErrorExplainer.explain(error))
    }

    @Test
    fun `mensaje con CLEARTEXT en otra excepcion tambien se explica`() {
        val error = IOException("Cleartext HTTP traffic to 192.168.1.20 not permitted")

        assertEquals(cleartextMessage, NetworkErrorExplainer.explain(error))
    }

    @Test
    fun `cleartext envuelto como causa se explica`() {
        val cause = UnknownServiceException("CLEARTEXT communication to 10.0.0.5 not permitted")
        val error = RuntimeException("fallo de red", cause)

        assertEquals(cleartextMessage, NetworkErrorExplainer.explain(error))
    }

    @Test
    fun `IOException generica no se explica`() {
        assertNull(NetworkErrorExplainer.explain(IOException("Connection refused")))
    }

    @Test
    fun `UnknownServiceException de TLS sin CLEARTEXT no se explica`() {
        val error = UnknownServiceException("Unable to find acceptable protocols. isFallback=false")

        assertNull(NetworkErrorExplainer.explain(error))
    }

    @Test
    fun `excepcion sin mensaje no se explica`() {
        assertNull(NetworkErrorExplainer.explain(IOException()))
    }
}
