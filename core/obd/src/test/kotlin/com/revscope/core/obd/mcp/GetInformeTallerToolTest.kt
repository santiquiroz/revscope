package com.revscope.core.obd.mcp

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetInformeTallerToolTest {

    @Test
    fun `es de lectura y exige sesión y formato json o html`() {
        val tool = tool()

        assertEquals("get_informe_taller", tool.name)
        assertEquals(McpPermiso.LECTURA, tool.permiso)
        val required = tool.inputSchema.getJSONArray("required")
        assertEquals(listOf("sesion_id", "formato"), (0 until required.length()).map(required::getString))
        val formato = tool.inputSchema.getJSONObject("properties").getJSONObject("formato")
        assertEquals("string", formato.getString("type"))
        assertEquals(listOf("json", "html"), (0 until 2).map(formato.getJSONArray("enum")::getString))
    }

    @Test
    fun `json devuelve directamente el informe serializado`() = runTest {
        val esperado = """{"sesion":{"id":7},"titulo":"Informe"}"""

        val respuesta = tool(json = esperado).call(argumentos(7, "json"))

        assertEquals(esperado, respuesta)
    }

    @Test
    fun `html devuelve un sobre sin aviso cuando cabe completo`() = runTest {
        val html = "<html><body>Informe ñ</body></html>"

        val respuesta = JSONObject(tool(html = html).call(argumentos(7, "html")))

        assertEquals(html, respuesta.getString("html"))
        assertFalse(respuesta.getBoolean("truncado"))
        assertTrue(respuesta.has("aviso"))
        assertTrue(respuesta.isNull("aviso"))
    }

    @Test
    fun `html se trunca a 200 KiB UTF-8 sin cortar caracteres y lo avisa`() = runTest {
        val html = "a".repeat(200 * 1024 - 1) + "€".repeat(50_000)

        val respuesta = JSONObject(tool(html = html).call(argumentos(7, "html")))

        val truncado = respuesta.getString("html")
        assertTrue(respuesta.getBoolean("truncado"))
        assertTrue(respuesta.getString("aviso").contains("200 KiB"))
        assertTrue(truncado.toByteArray(Charsets.UTF_8).size <= 200 * 1024)
        assertFalse(truncado.contains('\uFFFD'))
        assertEquals("a", truncado.takeLast(1))
    }

    @Test
    fun `argumentos ausentes o inválidos devuelven un error claro`() = runTest {
        val tool = tool()

        assertTrue(error(tool, JSONObject()).contains("sesion_id"))
        assertTrue(error(tool, JSONObject().put("sesion_id", "siete").put("formato", "json")).contains("sesion_id"))
        assertTrue(error(tool, JSONObject().put("sesion_id", 7.5).put("formato", "json")).contains("entero positivo"))
        assertTrue(error(tool, argumentos(0, "json")).contains("entero positivo"))
        assertTrue(error(tool, argumentos(7, "pdf")).lowercase().contains("formato"))
        assertTrue(error(tool, JSONObject().put("sesion_id", 7).put("formato", 4)).contains("Formato no válido"))
        assertTrue(error(tool, JSONObject().put("sesion_id", 7)).contains("formato"))
    }

    @Test
    fun `sesión inexistente devuelve error con su id`() = runTest {
        listOf("json", "html").forEach { formato ->
            val respuesta = JSONObject(tool(disponible = false).call(argumentos(99, formato)))

            assertTrue(respuesta.getString("error").contains("99"))
            assertTrue(respuesta.getString("error").contains("No existe"))
        }
    }

    private suspend fun error(tool: GetInformeTallerTool, argumentos: JSONObject): String =
        JSONObject(tool.call(argumentos)).getString("error")

    private fun argumentos(sesionId: Long, formato: String): JSONObject = JSONObject()
        .put("sesion_id", sesionId)
        .put("formato", formato)

    private fun tool(
        json: String = "{}",
        html: String = "<html></html>",
        disponible: Boolean = true,
    ): GetInformeTallerTool = GetInformeTallerTool(
        generarJson = { id -> json.takeIf { disponible && id == 7L } },
        generarHtml = { id -> html.takeIf { disponible && id == 7L } },
    )
}
