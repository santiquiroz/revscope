package com.revscope.core.obd.mcp

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.grafica.FuenteVref
import com.revscope.core.obd.taller.grafica.PreferenciasVref
import com.revscope.core.obd.taller.grafica.PreferenciasVrefEnMemoria
import com.revscope.core.obd.taller.grafica.VrefSesion
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.MontajePrueba
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.sesion.BENELLI
import com.revscope.core.obd.taller.sesion.EventoTaller
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

    private class Tools(val m: MontajePrueba, fuenteVref: FuenteVref) {
        val iniciar = IniciarPruebaGuiadaTool(m.controlador, m.enlace, fuenteVref)
        val avanzar = AvanzarPruebaGuiadaTool(m.controlador, m.enlace)
        val cancelar = CancelarPruebaGuiadaTool(m.controlador, m.enlace)
        val get = GetPruebaGuiadaTool(m.controlador, m.enlace)
    }

    private fun TestScope.tools(preferencias: PreferenciasVref = PreferenciasVrefEnMemoria()): Tools {
        val m = MontajePrueba(this, carpeta.root)
        val fuenteVref = FuenteVref(preferencias, m.repositorio) { m.vehiculo }
        return Tools(m, fuenteVref)
    }

    private suspend fun McpTool.llamar(argumentos: JSONObject = JSONObject()) = JSONObject(call(argumentos))

    private fun tps(): JSONObject = JSONObject().put("tipo", "TPS_BARRIDO")

    private suspend fun TestScope.resultadoVref(t: Tools, argumentos: JSONObject = tps().put("voz", false)): JSONObject {
        val inicio = t.iniciar.llamar(argumentos)
        assertTrue(inicio.toString(), inicio.getBoolean("iniciada"))
        t.m.sostenerLosCuatroPasos(this)
        advanceTimeBy(8_100)
        t.m.controlador.estado.first { it is EstadoPrueba.Terminada }
        return t.get.llamar().getJSONObject("resultado").getJSONObject("detalle").getJSONObject("vref")
    }

    private suspend fun agregarMedicionVref(t: Tools, voltios: Double) {
        val sesion = t.m.abrirSesion()
        val payload = """{"lecturas":[{"funcion":"REF_5V","condicion":"KOEO","valor":$voltios,"unidad":"V"}]}"""
        t.m.repositorio.agregarEvento(
            EventoTaller(
                sesionId = sesion.id,
                instante = t.m.reloj(),
                tipo = TipoEvento.MEDICION_MULTIMETRO,
                titulo = "Medición de referencia",
                payloadJson = payload,
            ),
        )
    }

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
    fun `iniciar rechaza un tipo que no existe y una referencia fuera de rango`() = runTest {
        val t = tools()

        val desconocido = t.iniciar.llamar(JSONObject().put("tipo", "COMPRESION"))
        val vrefMala = t.iniciar.llamar(tps().put("vref_v", 12.0))

        assertTrue(desconocido.getString("error").contains("TPS_BARRIDO"))
        assertTrue(desconocido.getString("error").contains("BATERIA_CARGA"))
        assertTrue(desconocido.getString("error").contains("MAP_BARO"))
        assertTrue(vrefMala.getString("error").contains("vref_v"))
        assertTrue(t.m.captura.configs.isEmpty())
    }

    @Test
    fun `vref ausente usa la referencia editada para el vehiculo`() = runTest {
        val preferencias = PreferenciasVrefEnMemoria().apply { guardadas[BENELLI.id] = 4.91 }
        val t = tools(preferencias)

        val vref = resultadoVref(t)

        assertEquals(4.91, vref.getDouble("voltios"), 0.0)
        assertEquals("Editado por ti", vref.getString("origen"))
    }

    @Test
    fun `vref ausente usa la referencia medida en la sesion si no hay una editada`() = runTest {
        val t = tools()
        agregarMedicionVref(t, 4.96)

        val vref = resultadoVref(t)

        assertEquals(4.96, vref.getDouble("voltios"), 0.0)
        assertEquals(VrefSesion.ORIGEN, vref.getString("origen"))
    }

    @Test
    fun `vref explicita reemplaza la editada y la medida`() = runTest {
        val preferencias = PreferenciasVrefEnMemoria().apply { guardadas[BENELLI.id] = 4.91 }
        val t = tools(preferencias)
        agregarMedicionVref(t, 4.96)

        val vref = resultadoVref(t, tps().put("voz", false).put("vref_v", 4.82))

        assertEquals(4.82, vref.getDouble("voltios"), 0.0)
        assertEquals("Editado por ti", vref.getString("origen"))
    }

    @Test
    fun `si falla la fuente de vref se usa la referencia tipica`() = runTest {
        val preferencias = object : PreferenciasVref {
            override suspend fun leer(vehiculoId: Long?): Double? = error("Preferencias no disponibles")
            override suspend fun guardar(vehiculoId: Long?, voltios: Double?) = Unit
        }
        val t = tools(preferencias)

        val vref = resultadoVref(t)

        assertEquals(ReferenciaVoltaje.TIPICA.voltios, vref.getDouble("voltios"), 0.0)
        assertEquals(ReferenciaVoltaje.TIPICA.origen, vref.getString("origen"))
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
