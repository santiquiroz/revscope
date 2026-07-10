package com.revscope.core.obd.mcp

import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class McpHttpServerTest {

    private fun session(
        method: Method = Method.POST,
        uri: String = "/mcp",
        authorized: Boolean = true,
        contentLength: String? = "20",
        body: String = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""",
    ): IHTTPSession {
        val session = mockk<IHTTPSession>()
        every { session.method } returns method
        every { session.uri } returns uri
        val headers = buildMap {
            if (authorized) put("authorization", "Bearer test-token")
            if (contentLength != null) put("content-length", contentLength)
        }
        every { session.headers } returns headers
        every { session.parseBody(any()) } answers {
            firstArg<MutableMap<String, String>>()["postData"] = body
        }
        return session
    }

    private fun server(): McpHttpServer =
        McpHttpServer("127.0.0.1", 0, McpDispatcher(emptyList()), "test-token")

    private fun bodyOf(response: Response): String = response.data.bufferedReader().readText()

    @Test
    fun `solicitud valida bajo el limite de tamano se procesa normalmente`() {
        val response = server().serve(session())

        assertEquals(Response.Status.OK, response.status)
        val result = JSONObject(bodyOf(response)).getJSONObject("result")
        assertEquals(0, result.getJSONArray("tools").length())
    }

    @Test
    fun `content-length ausente responde 413 sin parsear el cuerpo`() {
        val session = session(contentLength = null)

        val response = server().serve(session)

        assertEquals(Response.Status.PAYLOAD_TOO_LARGE, response.status)
    }

    @Test
    fun `content-length mayor a 64000 responde 413 sin parsear el cuerpo`() {
        val session = session(contentLength = "64001")

        val response = server().serve(session)

        assertEquals(Response.Status.PAYLOAD_TOO_LARGE, response.status)
    }

    @Test
    fun `content-length igual al limite de 64000 se acepta`() {
        val response = server().serve(session(contentLength = "64000"))

        assertEquals(Response.Status.OK, response.status)
    }

    @Test
    fun `content-length no numerico responde 413`() {
        val response = server().serve(session(contentLength = "no-es-un-numero"))

        assertEquals(Response.Status.PAYLOAD_TOO_LARGE, response.status)
    }

    @Test
    fun `sin autorizacion responde 401 antes de revisar el tamano`() {
        val response = server().serve(session(authorized = false, contentLength = null))

        assertEquals(Response.Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `metodo o ruta invalidos responden 404 sin autenticar`() {
        val response = server().serve(session(method = Method.GET))

        assertEquals(Response.Status.NOT_FOUND, response.status)
    }
}
