package com.revscope.core.obd.mcp

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.MontajePrueba
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.TipoEvento
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PruebaGuiadaToolsTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private class Tools(val m: MontajePrueba) {
        val iniciar = IniciarPruebaGuiadaTool(m.controlador, m.enlace)
        val avanzar = AvanzarPruebaGuiadaTool(m.controlador, m.enlace)
        val cancelar = CancelarPruebaGuiadaTool(m.controlador, m.enlace)
        val get = GetPruebaGuiadaTool(m.controlador, m.enlace)
    }

    private fun TestScope.tools() = Tools(MontajePrueba(this, carpeta.root))

    private suspend fun McpTool.llamar(argumentos: JSONObject = JSONObject()) = JSONObject(call(argumentos))

    private fun tps(): JSONObject = JSONObject().put("tipo", "TPS_BARRIDO")

    @Test
    fun `permisos - las tres de control piden control y get es de lectura`() = runTest {
        val t = tools()

        assertEquals(McpPermiso.CONTROL, t.iniciar.permiso)
        assertEquals(McpPermiso.CONTROL, t.avanzar.permiso)
        assertEquals(McpPermiso.CONTROL, t.cancelar.permiso)
        assertEquals(McpPermiso.LECTURA, t.get.permiso)
        assertEquals(listOf("tipo"), (0 until t.iniciar.inputSchema.getJSONArray("required").length()).map {
            t.iniciar.inputSchema.getJSONArray("required").getString(it)
        })
    }

    @Test
    fun `get sin prueba dice que esta inactiva y cuales hay`() = runTest {
        val r = tools().get.llamar()

        assertEquals("INACTIVA", r.getString("estado"))
        assertEquals("TPS_BARRIDO", r.getJSONArray("disponibles").getString(0))
    }

    @Test
    fun `iniciar devuelve el id y el primer paso y guia por voz`() = runTest {
        val t = tools()

        val r = t.iniciar.llamar(tps())

        assertTrue(r.getBoolean("iniciada"))
        assertEquals("EN_PASO", r.getString("estado"))
        assertTrue(r.getString("pruebaId").startsWith("prueba-"))
        assertEquals(1, r.getInt("paso"))
        assertEquals(5, r.getInt("total"))
        assertEquals("POSICIONANDO", r.getString("fase"))
        assertEquals("CERRADO_1", r.getJSONObject("pasoActual").getString("clave"))
        assertTrue(r.getString("mensaje"), r.getString("mensaje").contains("avanzar_prueba_guiada"))
        assertEquals(1, t.m.voz.dichos.size)
        t.m.controlador.cancelar()
    }

    @Test
    fun `iniciar rechaza un tipo sin analizador y una referencia fuera de rango`() = runTest {
        val t = tools()

        val sinAnalizador = t.iniciar.llamar(JSONObject().put("tipo", "MAP_BARO"))
        val vrefMala = t.iniciar.llamar(tps().put("vref_v", 12.0))

        assertTrue(sinAnalizador.getString("error").contains("TPS_BARRIDO"))
        assertTrue(vrefMala.getString("error").contains("vref_v"))
        assertTrue(t.m.captura.configs.isEmpty())
    }

    @Test
    fun `iniciar con una precondicion fallando no arranca y dice que hacer`() = runTest {
        val t = tools()
        t.m.enlace.lecturas = mapOf("0C" to ObdReading("0C", 1_500.0, "rpm", t.m.reloj()))

        val r = t.iniciar.llamar(tps())

        assertFalse(r.getBoolean("iniciada"))
        assertEquals("VERIFICANDO", r.getString("estado"))
        val fallando = (0 until r.getJSONArray("precondiciones").length())
            .map { r.getJSONArray("precondiciones").getJSONObject(it) }
            .single { !it.getBoolean("cumple") }
        assertEquals("Motor encendido (1500 rpm)", fallando.getString("texto"))
        assertTrue(r.getString("mensaje").contains("Apaga el motor"))
    }

    @Test
    fun `avanzar es el listo del paso y get muestra la cuenta con el valor en vivo`() = runTest {
        val t = tools()
        t.iniciar.llamar(tps().put("voz", false))

        val avanzado = t.avanzar.llamar()
        advanceTimeBy(1_050)
        t.m.enlace.lecturas = mapOf("11" to ObdReading("11", 2.35, "%", t.m.reloj()))
        val durante = t.get.llamar()

        assertEquals("SOSTENIENDO", avanzado.getString("fase"))
        assertEquals(5_000, avanzado.getLong("restanteMs"))
        assertEquals(4_000, durante.getLong("restanteMs"))
        assertEquals(2.35, durante.getJSONObject("valorActual").getDouble("valor"), 0.0)
        assertTrue(t.m.voz.dichos.isEmpty())
        t.m.controlador.cancelar()
    }

    @Test
    fun `avanzar y cancelar sin prueba en curso dan error`() = runTest {
        val t = tools()

        assertTrue(t.avanzar.llamar().has("error"))
        assertTrue(t.cancelar.llamar().has("error"))
    }

    @Test
    fun `cancelar detiene la prueba y la captura`() = runTest {
        val t = tools()
        t.iniciar.llamar(tps())

        val r = t.cancelar.llamar()

        assertEquals("CANCELADA", r.getString("estado"))
        assertEquals("Cancelada desde el MCP", r.getString("motivo"))
        assertFalse(t.m.captura.activa())
    }

    @Test
    fun `al terminar get trae el veredicto y el evento queda en la sesion con origen mcp`() = runTest {
        val t = tools()
        val sesion = t.m.abrirSesion()
        t.iniciar.llamar(tps().put("vref_v", 5.0))

        t.m.sostenerLosCuatroPasos(this)
        advanceTimeBy(8_100)
        t.m.controlador.estado.first { it is EstadoPrueba.Terminada }
        val r = t.get.llamar()

        assertEquals("TERMINADA", r.getString("estado"))
        assertTrue(r.getBoolean("guardadaEnSesion"))
        val resultado = r.getJSONObject("resultado")
        assertEquals("FALLA", resultado.getString("veredicto"))
        assertEquals("SENAL_BAJA_TODO_EL_RECORRIDO", resultado.getJSONObject("detalle").getString("patron"))
        assertEquals("Editado por ti", resultado.getJSONObject("detalle").getJSONObject("vref").getString("origen"))
        assertTrue(resultado.getString("interpretacion").contains("P0122"))
        val evento = t.m.repositorio.eventos(sesion.id).single { it.tipo == TipoEvento.PRUEBA_GUIADA }
        assertEquals(OrigenEvento.MCP, evento.origen)
    }
}
