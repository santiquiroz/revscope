package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.session.ObdSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetDtcToolTest {

    private fun sessionManager(
        connectionState: ConnectionState = ConnectionState.Connected("ELM327"),
        currentSessionId: Long? = null,
    ): ObdSessionManager {
        val manager = mockk<ObdSessionManager>()
        every { manager.connectionState } returns MutableStateFlow(connectionState)
        every { manager.currentSessionId } returns MutableStateFlow(currentSessionId)
        coEvery { manager.readActiveDtc() } returns Result.success(listOf(DtcCode("P0300", DtcMode.Active)))
        return manager
    }

    @Test
    fun `vehiculo no conectado responde sin intentar leer dtc`() = runTest {
        val manager = sessionManager(connectionState = ConnectionState.Disconnected)
        val tool = GetDtcTool(manager)

        val response = JSONObject(tool.call(JSONObject()))

        assertFalse(response.getBoolean("conectado"))
        coVerify(exactly = 0) { manager.readActiveDtc() }
    }

    @Test
    fun `viaje activo y conectado rechaza la lectura de dtc`() = runTest {
        val manager = sessionManager(currentSessionId = 42L)
        val tool = GetDtcTool(manager)

        val response = JSONObject(tool.call(JSONObject()))

        assertTrue(response.has("error"))
        assertTrue(response.getString("error").contains("vehículo en marcha"))
        coVerify(exactly = 0) { manager.readActiveDtc() }
    }

    @Test
    fun `sin viaje activo lee dtc del ecu`() = runTest {
        val manager = sessionManager()
        val tool = GetDtcTool(manager)

        val response = JSONObject(tool.call(JSONObject()))

        assertTrue(response.getBoolean("conectado"))
        assertEquals("P0300", response.getJSONArray("codigos").getString(0))
        assertFalse(response.has("cache"))
        coVerify(exactly = 1) { manager.readActiveDtc() }
    }

    @Test
    fun `segunda llamada dentro de la ventana de 30s responde con cache sin releer el ecu`() = runTest {
        val manager = sessionManager()
        var now = 1_000L
        val tool = GetDtcTool(manager) { now }

        tool.call(JSONObject())
        now += 20_000L
        val response = JSONObject(tool.call(JSONObject()))

        assertTrue(response.getBoolean("cache"))
        coVerify(exactly = 1) { manager.readActiveDtc() }
    }

    @Test
    fun `llamada tras vencer la ventana de 30s vuelve a leer el ecu`() = runTest {
        val manager = sessionManager()
        var now = 1_000L
        val tool = GetDtcTool(manager) { now }

        tool.call(JSONObject())
        now += 30_001L
        val response = JSONObject(tool.call(JSONObject()))

        assertFalse(response.has("cache"))
        coVerify(exactly = 2) { manager.readActiveDtc() }
    }
}
