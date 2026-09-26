package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.telemetry.SamplingPreset
import com.revscope.core.obd.telemetry.captura.CapturaRapida
import com.revscope.core.obd.telemetry.captura.ConfigCaptura
import com.revscope.core.obd.telemetry.captura.EstadoCaptura
import com.revscope.core.obd.telemetry.captura.InfoAdaptador
import com.revscope.core.obd.telemetry.captura.InicioCaptura
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.telemetry.captura.PaginaCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.telemetry.captura.ResumenPid
import com.revscope.core.obd.telemetry.captura.TecnicaCaptura
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuestreoToolsTest {

    private val inicio = InicioCaptura(
        id = "cap-1",
        pidsAceptados = listOf("49", "4A", "11"),
        pidsNoSoportados = listOf("4B"),
        lotes = listOf(listOf("49", "4A", "11")),
        tecnicas = setOf(TecnicaCaptura.MULTI_PID, TecnicaCaptura.SUFIJO_1),
        duracionMaxMs = 60_000,
    )

    private val captura = mockk<CapturaRapida>(relaxed = true).also {
        every { it.estado } returns MutableStateFlow(EstadoCaptura.Activa(inicio, 1_790_000_000_000L, limiteHz = null))
        every { it.estadisticas } returns MutableStateFlow(null)
        every { it.ultimoResumen } returns MutableStateFlow(null)
    }

    private fun manager(conexion: ConnectionState = ConnectionState.Connected("Vlink")) =
        mockk<ObdSessionManager>(relaxed = true).also {
            every { it.connectionState } returns MutableStateFlow(conexion)
            every { it.captura } returns captura
            every { it.muestreoPreset } returns MutableStateFlow(SamplingPreset.ESTANDAR_2S)
            every { it.infoAdaptador() } returns InfoAdaptador("Vlink", "ELM327 v2.2", "6", esCan = true)
        }

    @Test
    fun `iniciar_captura acota la duracion a 600 s y devuelve lotes y tecnicas`() = runTest {
        val config = slot<ConfigCaptura>()
        coEvery { captura.iniciar(capture(config)) } returns Result.success(inicio)
        val args = JSONObject().put("pids", JSONArray(listOf("49", "4A", "11", "4B"))).put("duracion_s", 5_000)

        val json = JSONObject(IniciarCapturaTool(manager()).call(args))

        assertEquals(600_000L, config.captured.duracionMaxMs)
        assertEquals(listOf("49", "4A", "11", "4B"), config.captured.pids)
        assertEquals("cap-1", json.getString("capturaId"))
        assertEquals("[\"4B\"]", json.getJSONArray("pidsNoSoportados").toString())
        assertEquals("[[\"49\",\"4A\",\"11\"]]", json.getJSONArray("lotes").toString())
    }

    @Test
    fun `iniciar_captura sin adaptador no toca la captura`() = runTest {
        val json = JSONObject(IniciarCapturaTool(manager(ConnectionState.Disconnected)).call(JSONObject()))

        assertFalse(json.getBoolean("conectado"))
        coVerify(exactly = 0) { captura.iniciar(any()) }
    }

    @Test
    fun `get_captura pagina en series columnares con el cursor siguiente`() = runTest {
        val muestras = listOf(
            MuestraCaptura(10, 12_000, "49", 14.901960784, 1, 20),
            MuestraCaptura(11, 12_000, "4A", 7.5, 1, 20),
            MuestraCaptura(12, 52_000, "49", 15.3, 2, 20),
        )
        every { captura.pagina("cap-1", 10, 2_000, null) } returns PaginaCaptura(muestras, seqSiguiente = 13, perdidas = 0)
        every { captura.inicioEpochMs("cap-1") } returns 1_790_000_000_000L

        val json = JSONObject(GetCapturaTool(manager()).call(JSONObject().put("capturaId", "cap-1").put("desde_seq", 10)))

        assertEquals(13L, json.getLong("seqSiguiente"))
        assertTrue(json.getBoolean("activa"))
        val serie = json.getJSONObject("series").getJSONObject("49")
        assertEquals("[12,52]", serie.getJSONArray("t_ms").toString())
        assertEquals(14.902, serie.getJSONArray("v").getDouble(0), 0.0)
    }

    @Test
    fun `get_captura de una captura que ya no esta en memoria da error`() = runTest {
        every { captura.pagina(any(), any(), any(), any()) } returns null

        val json = JSONObject(GetCapturaTool(manager()).call(JSONObject().put("capturaId", "vieja")))

        assertTrue(json.has("error"))
    }

    @Test
    fun `detener_captura devuelve el resumen por pid`() = runTest {
        coEvery { captura.detener(any()) } returns ResumenCaptura(
            id = "cap-1",
            duracionMs = 12_345,
            porPid = listOf(ResumenPid("49", n = 300, hz = 24.3, min = 0.0, max = 99.6, media = 40.123456)),
            latenciaP50Ms = 31.0,
            latenciaP95Ms = 48.0,
            motivoFin = "detenida desde el MCP",
            rutaCsv = "/data/cache/exports/revscope-captura-x.csv",
        )

        val json = JSONObject(DetenerCapturaTool(manager()).call(JSONObject().put("capturaId", "cap-1")))

        assertEquals(12.3, json.getDouble("duracionS"), 0.0)
        assertEquals(300, json.getJSONObject("porPid").getJSONObject("49").getInt("n"))
        assertEquals(40.123, json.getJSONObject("porPid").getJSONObject("49").getDouble("media"), 0.0)
        assertEquals("detenida desde el MCP", json.getString("motivoFin"))
    }

    @Test
    fun `set_muestreo valida el preset y lo guarda`() = runTest {
        val m = manager()
        val tool = SetMuestreoTool(m)

        val ok = JSONObject(tool.call(JSONObject().put("preset", "250ms")))
        val malo = JSONObject(tool.call(JSONObject().put("preset", "10ms")))

        assertEquals("250ms", ok.getString("preset"))
        assertTrue(malo.has("error"))
        coVerify(exactly = 1) { m.cambiarPresetMuestreo(SamplingPreset.CUARTO_SEGUNDO) }
    }

    @Test
    fun `get_muestreo informa preset captura y adaptador`() = runTest {
        val json = JSONObject(GetMuestreoTool(manager()).call(JSONObject()))

        assertEquals("estandar_2s", json.getString("preset"))
        assertTrue(json.getBoolean("capturaActiva"))
        assertEquals("cap-1", json.getString("capturaId"))
        assertEquals("ELM327 v2.2", json.getJSONObject("adaptador").getString("elm"))
    }

    @Test
    fun `las tools que cambian algo piden control y las de lectura no`() {
        val m = manager()
        assertEquals(McpPermiso.LECTURA, GetMuestreoTool(m).permiso)
        assertEquals(McpPermiso.LECTURA, GetCapturaTool(m).permiso)
        assertEquals(McpPermiso.CONTROL, SetMuestreoTool(m).permiso)
        assertEquals(McpPermiso.CONTROL, IniciarCapturaTool(m).permiso)
        assertEquals(McpPermiso.CONTROL, DetenerCapturaTool(m).permiso)
    }
}
