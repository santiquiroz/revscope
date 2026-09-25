package com.revscope.core.common.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevScopeHttpTest {

    @Test
    fun `el User-Agent empieza con RevScope y una versión`() {
        val versionPrefix = Regex("""^RevScope/\d+\.\d+\S* """)

        assertTrue(RevScopeHttp.USER_AGENT, versionPrefix.containsMatchIn(RevScopeHttp.USER_AGENT))
    }

    @Test
    fun `el User-Agent trae una URL de contacto`() {
        assertTrue(RevScopeHttp.USER_AGENT.contains("(+https://github.com/santiquiroz/revscope)"))
    }

    @Test
    fun `el User-Agent no se confunde con el de Dalvik`() {
        assertFalse(RevScopeHttp.USER_AGENT.contains("Dalvik"))
    }

    @Test
    fun `userAgent arma el formato con la versión recibida`() {
        assertEquals(
            "RevScope/2.0.1 (+https://github.com/santiquiroz/revscope)",
            RevScopeHttp.userAgent("2.0.1"),
        )
    }
}
