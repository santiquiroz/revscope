package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.obd.session.ObdSessionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GetEstadoToolTest {

    @Test
    fun `incluye viaje permisos y la edad de cada lectura`() = runTest {
        val manager = mockk<ObdSessionManager>()
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connected("Vlink"))
        every { manager.activeProfile } returns MutableStateFlow(null)
        every { manager.isGpsSessionActive } returns MutableStateFlow(false)
        every { manager.estadoViaje } returns MutableStateFlow(EstadoViaje.Grabando(sessionId = 3L, inicioMs = 500L))
        every { manager.readings } returns MutableStateFlow(mapOf("0C" to ObdReading("0C", 800.0, "rpm", timestamp = 9_700L)))
        val permisos = mockk<McpPermisosProvider>()
        coEvery { permisos.actuales() } returns setOf(McpPermiso.LECTURA, McpPermiso.CONTROL)

        val json = JSONObject(GetEstadoTool(manager, permisos) { 10_000L }.call(JSONObject()))

        val viaje = json.getJSONObject("viaje")
        assertEquals("grabando", viaje.getString("estado"))
        assertEquals(3L, viaje.getLong("id"))
        assertEquals("[\"control\",\"lectura\"]", json.getJSONArray("permisos").toString())
        assertEquals(300L, json.getJSONObject("lecturasEnVivo").getJSONObject("0C").getLong("edadMs"))
    }
}
