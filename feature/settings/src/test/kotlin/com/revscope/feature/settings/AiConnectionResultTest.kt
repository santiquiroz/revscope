package com.revscope.feature.settings

import com.revscope.core.common.net.NetworkErrorExplainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.UnknownServiceException

class AiConnectionResultTest {

    @Test
    fun `http bloqueado por Android muestra la explicacion y no el mensaje crudo`() {
        val error = UnknownServiceException(
            "CLEARTEXT communication to 192.168.1.20 not permitted by network security policy",
        )

        val result = aiConnectionFailure(error)

        assertFalse(result.success)
        assertEquals("No se pudo conectar — ${NetworkErrorExplainer.CLEARTEXT_BLOCKED}", result.message)
    }

    @Test
    fun `error generico muestra su mensaje`() {
        val result = aiConnectionFailure(IOException("Connection refused"))

        assertEquals("No se pudo conectar — Connection refused", result.message)
    }

    @Test
    fun `error sin mensaje sugiere revisar llave y red`() {
        val result = aiConnectionFailure(IOException())

        assertEquals("No se pudo conectar — revisa la llave y la red", result.message)
    }

    @Test
    fun `mensaje largo se recorta a 200 caracteres`() {
        val result = aiConnectionFailure(IOException("x".repeat(500)))

        assertEquals("No se pudo conectar — " + "x".repeat(200), result.message)
    }
}
