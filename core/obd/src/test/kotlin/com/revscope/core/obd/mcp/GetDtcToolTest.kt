package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.diagnostics.DtcLectura
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.diagnostics.FreezeFrame
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.protocol.DtcServicio
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.RegistroTaller
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetDtcToolTest {

    private val registry = PidRegistry(TestPids.load())
    private val registro = mockk<RegistroTaller>(relaxed = true)

    private val scan = DtcScan(
        activos = listOf(DtcCode("P0300", DtcMode.Active)),
        pendientes = listOf(DtcCode("P0420", DtcMode.Pending)),
        permanentes = emptyList(),
        milEncendida = true,
        conteoSegunEcu = 1,
        freezeFrame = FreezeFrame("P0300", listOf(ObdReading("0C", 1724.0, "rpm"))),
        crudo = mapOf("03" to "43010300"),
        errores = emptyList(),
    )

    private fun sessionManager(
        connectionState: ConnectionState = ConnectionState.Connected("ELM327"),
        estado: EstadoViaje = EstadoViaje.EnlaceSinViaje,
    ): ObdSessionManager {
        val manager = mockk<ObdSessionManager>()
        every { manager.connectionState } returns MutableStateFlow(connectionState)
        every { manager.estadoViaje } returns MutableStateFlow(estado)
        coEvery { manager.leerDtcCompleto(any(), any()) } returns Result.success(scan)
        return manager
    }

    @Test
    fun `vehiculo no conectado responde sin intentar leer dtc`() = runTest {
        val manager = sessionManager(connectionState = ConnectionState.Disconnected)

        val response = JSONObject(GetDtcTool(manager, registry, registro).call(JSONObject()))

        assertFalse(response.getBoolean("conectado"))
        coVerify(exactly = 0) { manager.leerDtcCompleto(any(), any()) }
    }

    @Test
    fun `viaje activo y conectado lee dtc bajo concesion`() = runTest {
        val manager = sessionManager(estado = EstadoViaje.Grabando(sessionId = 42L, inicioMs = 0L))

        val response = JSONObject(GetDtcTool(manager, registry, registro).call(JSONObject()))

        assertTrue(response.getBoolean("conectado"))
        assertEquals("grabando", response.getString("viaje"))
        assertEquals("P0300", response.getJSONArray("activos").getString(0))
        coVerify(exactly = 1) { manager.leerDtcCompleto("mcp:get_dtc", any()) }
    }

    @Test
    fun `devuelve activos pendientes permanentes mil y freeze frame con su causante`() = runTest {
        val response = JSONObject(GetDtcTool(sessionManager(), registry, registro).call(JSONObject()))

        assertEquals("P0300", response.getJSONArray("codigos").getString(0))
        assertEquals("P0420", response.getJSONArray("pendientes").getString(0))
        assertEquals(0, response.getJSONArray("permanentes").length())
        assertTrue(response.getBoolean("mil"))
        assertEquals(1, response.getInt("conteoSegunEcu"))
        val ff = response.getJSONObject("freezeFrame")
        assertEquals("P0300", ff.getString("dtcCausante"))
        assertEquals("RPM Motor", ff.getJSONArray("valores").getJSONObject(0).getString("nombre"))
        assertFalse(response.has("crudo"))
    }

    @Test
    fun `los argumentos eligen modos freeze frame y crudo`() = runTest {
        val manager = sessionManager()
        val opciones = slot<DtcLectura>()
        coEvery { manager.leerDtcCompleto(any(), capture(opciones)) } returns Result.success(scan)
        val args = JSONObject()
            .put("modos", org.json.JSONArray().put("pendientes"))
            .put("freeze_frame", false)
            .put("incluir_crudo", true)

        val response = JSONObject(GetDtcTool(manager, registry, registro).call(args))

        assertEquals(setOf(DtcServicio.PENDIENTES), opciones.captured.servicios)
        assertFalse(opciones.captured.freezeFrame)
        assertEquals("43010300", response.getJSONObject("crudo").getString("03"))
    }

    @Test
    fun `segunda llamada igual dentro de 10 s responde con cache sin releer el ecu`() = runTest {
        val manager = sessionManager()
        var now = 1_000L
        val tool = GetDtcTool(manager, registry, registro) { now }

        tool.call(JSONObject())
        now += 9_000L
        val response = JSONObject(tool.call(JSONObject()))

        assertTrue(response.getBoolean("cache"))
        coVerify(exactly = 1) { manager.leerDtcCompleto(any(), any()) }
    }

    @Test
    fun `otra combinacion de argumentos antes de 5 s pide esperar`() = runTest {
        val manager = sessionManager()
        var now = 1_000L
        val tool = GetDtcTool(manager, registry, registro) { now }

        tool.call(JSONObject())
        now += 2_000L
        val response = JSONObject(tool.call(JSONObject().put("freeze_frame", false)))

        assertTrue(response.getString("error").contains("reintenta en 3 s"))
        coVerify(exactly = 1) { manager.leerDtcCompleto(any(), any()) }
    }

    @Test
    fun `llamada tras vencer la cache vuelve a leer el ecu`() = runTest {
        val manager = sessionManager()
        var now = 1_000L
        val tool = GetDtcTool(manager, registry, registro) { now }

        tool.call(JSONObject())
        now += 10_001L
        val response = JSONObject(tool.call(JSONObject()))

        assertFalse(response.has("cache"))
        coVerify(exactly = 2) { manager.leerDtcCompleto(any(), any()) }
    }

    @Test
    fun `una lectura real queda en la sesion del taller con origen MCP y la de cache no`() = runTest {
        val manager = sessionManager()
        var now = 1_000L
        val tool = GetDtcTool(manager, registry, registro) { now }

        tool.call(JSONObject())
        now += 9_000L
        tool.call(JSONObject())

        coVerify(exactly = 1) { registro.anotarLecturaDtc(scan, OrigenEvento.MCP) }
    }

    @Test
    fun `una lectura fallida no se anota en la sesion`() = runTest {
        val manager = sessionManager()
        coEvery { manager.leerDtcCompleto(any(), any()) } returns Result.failure(IllegalStateException("Not connected"))

        GetDtcTool(manager, registry, registro).call(JSONObject())

        coVerify(exactly = 0) { registro.anotarLecturaDtc(any(), any()) }
    }

    @Test
    fun `una lectura fallida no se cachea`() = runTest {
        val manager = sessionManager()
        coEvery { manager.leerDtcCompleto(any(), any()) } returns Result.failure(IllegalStateException("Not connected"))
        var now = 1_000L
        val tool = GetDtcTool(manager, registry, registro) { now }

        tool.call(JSONObject())
        now += 6_000L
        tool.call(JSONObject())

        coVerify(exactly = 2) { manager.leerDtcCompleto(any(), any()) }
    }
}
