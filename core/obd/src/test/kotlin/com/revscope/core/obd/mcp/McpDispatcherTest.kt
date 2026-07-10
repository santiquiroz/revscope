package com.revscope.core.obd.mcp

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpDispatcherTest {

    private fun fakeTool(
        name: String = "get_estado",
        result: String = """{"ok":true}""",
        onCall: ((JSONObject) -> Unit)? = null,
    ) = object : McpTool {
        override val name = name
        override val description = "Tool de prueba"
        override val inputSchema: JSONObject = JSONObject().put("type", "object")
        override suspend fun call(arguments: JSONObject): String {
            onCall?.invoke(arguments)
            return result
        }
    }

    @Test
    fun `initialize responde protocolo capacidades e info del servidor`() = runTest {
        val dispatcher = McpDispatcher(emptyList(), serverVersion = "9.9.9")

        val response = JSONObject(dispatcher.dispatch("""{"jsonrpc":"2.0","id":1,"method":"initialize"}""")!!)

        assertEquals("2.0", response.getString("jsonrpc"))
        assertEquals(1, response.getInt("id"))
        val result = response.getJSONObject("result")
        assertEquals("2025-03-26", result.getString("protocolVersion"))
        assertTrue(result.getJSONObject("capabilities").has("tools"))
        assertEquals("revscope", result.getJSONObject("serverInfo").getString("name"))
        assertEquals("9.9.9", result.getJSONObject("serverInfo").getString("version"))
    }

    @Test
    fun `notifications initialized no produce respuesta`() = runTest {
        val dispatcher = McpDispatcher(emptyList())

        val response = dispatcher.dispatch("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")

        assertNull(response)
    }

    @Test
    fun `tools list expone nombre descripcion y schema de cada tool registrada`() = runTest {
        val dispatcher = McpDispatcher(listOf(fakeTool(name = "get_estado"), fakeTool(name = "get_dtc")))

        val response = JSONObject(dispatcher.dispatch("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")!!)

        val tools = response.getJSONObject("result").getJSONArray("tools")
        assertEquals(2, tools.length())
        val first = tools.getJSONObject(0)
        assertEquals("get_estado", first.getString("name"))
        assertEquals("Tool de prueba", first.getString("description"))
        assertEquals("object", first.getJSONObject("inputSchema").getString("type"))
    }

    @Test
    fun `tools call ejecuta la tool con sus argumentos y envuelve el resultado como texto`() = runTest {
        var received: JSONObject? = null
        val dispatcher = McpDispatcher(
            listOf(fakeTool(result = """{"km":123}""", onCall = { received = it })),
        )
        val request = """{"jsonrpc":"2.0","id":3,"method":"tools/call",""" +
            """"params":{"name":"get_estado","arguments":{"limit":5}}}"""

        val response = JSONObject(dispatcher.dispatch(request)!!)

        val content = response.getJSONObject("result").getJSONArray("content")
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("""{"km":123}""", content.getJSONObject(0).getString("text"))
        assertEquals(5, received?.getInt("limit"))
    }

    @Test
    fun `tools call con nombre desconocido responde invalid params`() = runTest {
        val dispatcher = McpDispatcher(listOf(fakeTool()))
        val request = """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"no_existe"}}"""

        val response = JSONObject(dispatcher.dispatch(request)!!)

        assertEquals(4, response.getInt("id"))
        assertEquals(-32602, response.getJSONObject("error").getInt("code"))
    }

    @Test
    fun `tools call sin nombre responde invalid params`() = runTest {
        val dispatcher = McpDispatcher(listOf(fakeTool()))
        val request = """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{}}"""

        val response = JSONObject(dispatcher.dispatch(request)!!)

        assertEquals(-32602, response.getJSONObject("error").getInt("code"))
    }

    @Test
    fun `metodo desconocido responde method not found`() = runTest {
        val dispatcher = McpDispatcher(emptyList())

        val response = JSONObject(dispatcher.dispatch("""{"jsonrpc":"2.0","id":5,"method":"foo/bar"}""")!!)

        assertEquals(-32601, response.getJSONObject("error").getInt("code"))
    }

    @Test
    fun `json malformado responde parse error`() = runTest {
        val dispatcher = McpDispatcher(emptyList())

        val response = JSONObject(dispatcher.dispatch("{esto no es json")!!)

        assertEquals(-32700, response.getJSONObject("error").getInt("code"))
    }

    @Test
    fun `el id se hace echo en la respuesta incluyendo ids de tipo string`() = runTest {
        val dispatcher = McpDispatcher(emptyList())

        val response = JSONObject(
            dispatcher.dispatch("""{"jsonrpc":"2.0","id":"abc-123","method":"tools/list"}""")!!,
        )

        assertEquals("abc-123", response.getString("id"))
    }

    @Test
    fun `tool que lanza excepcion responde error sin tumbar el dispatcher`() = runTest {
        val throwingTool = object : McpTool {
            override val name = "explota"
            override val description = "d"
            override val inputSchema: JSONObject = JSONObject()
            override suspend fun call(arguments: JSONObject): String = error("boom")
        }
        val dispatcher = McpDispatcher(listOf(throwingTool))
        val request = """{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"explota","arguments":{}}}"""

        val response = JSONObject(dispatcher.dispatch(request)!!)

        assertEquals(6, response.getInt("id"))
        assertEquals(-32602, response.getJSONObject("error").getInt("code"))
    }
}
