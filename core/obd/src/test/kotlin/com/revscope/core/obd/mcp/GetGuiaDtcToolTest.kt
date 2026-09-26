package com.revscope.core.obd.mcp

import com.revscope.core.obd.taller.dtc.BaseConocimientoDtc
import com.revscope.core.obd.taller.dtc.GuiaDePrueba
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetGuiaDtcToolTest {

    private val tool = GetGuiaDtcTool(GuiaDePrueba.baseReal())

    private suspend fun llamar(codigo: Any?, conTool: GetGuiaDtcTool = tool): JSONObject {
        val argumentos = JSONObject().apply { if (codigo != null) put("codigo", codigo) }
        return JSONObject(conTool.call(argumentos))
    }

    @Test
    fun `es de lectura, pide el código y se llama get_guia_dtc`() {
        assertEquals("get_guia_dtc", tool.name)
        assertEquals(McpPermiso.LECTURA, tool.permiso)
        assertEquals("codigo", tool.inputSchema.getJSONArray("required").getString(0))
    }

    @Test
    fun `P0122 devuelve la guía local con verificaciones numeradas y sus acciones`() = runTest {
        val r = llamar("p0122")

        assertEquals("P0122", r.getString("codigo"))
        assertTrue(r.getBoolean("en_guia_local"))
        assertEquals("Orientación general, no es el procedimiento del fabricante", r.getString("aviso"))
        assertTrue(r.getString("fuente_titulo").contains("SAE J2012"))
        val guia = r.getJSONObject("guia")
        assertTrue(guia.getString("titulo").contains("señal baja"))
        assertEquals("REVISAR_PRONTO", guia.getString("urgencia"))
        assertTrue(guia.getJSONArray("causas").length() >= 2)
        val verificaciones = guia.getJSONArray("verificaciones")
        assertEquals(1, verificaciones.getJSONObject(0).getInt("n"))
        val segunda = verificaciones.getJSONObject(1)
        assertEquals("PRUEBA:TPS_BARRIDO", segunda.getString("accion"))
        assertEquals("Prueba guiada: Barrido del TPS", segunda.getString("accion_texto"))
        assertFalse(verificaciones.getJSONObject(0).has("accion"))
        assertTrue(guia.getJSONArray("notas_moto").length() > 0)
        assertEquals("genérico, definido por sae", r.getJSONObject("estructura").getString("ambito_texto").lowercase())
    }

    @Test
    fun `un genérico fuera de la guía explica solo la estructura`() = runTest {
        val r = llamar("P0442")

        assertFalse(r.getBoolean("en_guia_local"))
        assertFalse(r.has("guia"))
        val estructura = r.getJSONObject("estructura")
        assertEquals("generico", estructura.getString("ambito"))
        assertEquals("Controles auxiliares de emisiones", estructura.getString("subsistema"))
        assertTrue(r.getString("mensaje").contains("SAE J2012"))
    }

    @Test
    fun `un código del fabricante no inventa descripción`() = runTest {
        val r = llamar("P1122")

        assertFalse(r.getBoolean("en_guia_local"))
        val estructura = r.getJSONObject("estructura")
        assertEquals("fabricante", estructura.getString("ambito"))
        assertFalse(estructura.has("subsistema"))
        assertEquals("Código del fabricante: consulta el manual del modelo", estructura.getString("explicacion"))
        assertTrue(r.getString("mensaje").contains("no inventa"))
    }

    @Test
    fun `códigos C, B y U se decodifican por sistema`() = runTest {
        assertEquals("Chasis", llamar("C0035").getJSONObject("estructura").getString("sistema"))
        assertEquals("Carrocería", llamar("B1000").getJSONObject("estructura").getString("sistema"))
        assertEquals("Red de comunicación entre módulos", llamar("U0100").getJSONObject("estructura").getString("sistema"))
    }

    @Test
    fun `código inválido o ausente responde con error y el formato esperado`() = runTest {
        listOf("P9", "hola", null, "").forEach { codigo ->
            val r = llamar(codigo)
            assertTrue("$codigo → $r", r.getString("error").contains("P0122"))
            assertFalse(r.has("estructura"))
        }
    }

    @Test
    fun `sin guía local cargable sigue decodificando la estructura`() = runTest {
        val sinGuia = GetGuiaDtcTool(BaseConocimientoDtc { "{ dañado" })

        val r = llamar("P0122", sinGuia)

        assertFalse(r.getBoolean("en_guia_local"))
        assertEquals("Medición de aire y combustible", r.getJSONObject("estructura").getString("subsistema"))
    }
}
