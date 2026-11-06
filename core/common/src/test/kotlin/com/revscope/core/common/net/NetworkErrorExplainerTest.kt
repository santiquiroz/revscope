package com.revscope.core.common.net

import com.revscope.core.common.R
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.UnknownServiceException

class NetworkErrorExplainerTest {

    private val resolverEs = XmlStringResolver.es(R::class.java)
    private val cleartextMessage = resolverEs.get(R.string.common_cleartext_blocked)

    @Test
    fun `UnknownServiceException de cleartext se explica`() {
        val error = UnknownServiceException(
            "CLEARTEXT communication to 192.168.1.20 not permitted by network security policy",
        )

        assertEquals(cleartextMessage, NetworkErrorExplainer.explain(error, resolverEs))
    }

    @Test
    fun `mensaje con CLEARTEXT en otra excepcion tambien se explica`() {
        val error = IOException("Cleartext HTTP traffic to 192.168.1.20 not permitted")

        assertEquals(cleartextMessage, NetworkErrorExplainer.explain(error, resolverEs))
    }

    @Test
    fun `cleartext envuelto como causa se explica`() {
        val cause = UnknownServiceException("CLEARTEXT communication to 10.0.0.5 not permitted")
        val error = RuntimeException("fallo de red", cause)

        assertEquals(cleartextMessage, NetworkErrorExplainer.explain(error, resolverEs))
    }

    @Test
    fun `IOException generica no se explica`() {
        assertNull(NetworkErrorExplainer.explain(IOException("Connection refused"), resolverEs))
    }

    @Test
    fun `UnknownServiceException de TLS sin CLEARTEXT no se explica`() {
        val error = UnknownServiceException("Unable to find acceptable protocols. isFallback=false")

        assertNull(NetworkErrorExplainer.explain(error, resolverEs))
    }

    @Test
    fun `excepcion sin mensaje no se explica`() {
        assertNull(NetworkErrorExplainer.explain(IOException(), resolverEs))
    }

    @Test
    fun `explicacion en ingles`() {
        val error = UnknownServiceException("CLEARTEXT communication not permitted")

        assertEquals(
            "Android blocks http:// outside this phone: use https (for example, a proxy with a certificate) or a tunnel",
            NetworkErrorExplainer.explain(error, XmlStringResolver.en(R::class.java)),
        )
    }
}
