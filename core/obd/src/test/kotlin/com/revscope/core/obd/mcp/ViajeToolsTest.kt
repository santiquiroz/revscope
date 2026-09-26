package com.revscope.core.obd.mcp

import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.MotivoFin
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.session.ViajeNoDisponibleException
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

class ViajeToolsTest {

    private fun manager(connection: ConnectionState = ConnectionState.Connected("Vlink")): ObdSessionManager =
        mockk<ObdSessionManager>().also {
            every { it.connectionState } returns MutableStateFlow(connection)
        }

    private fun sesion(id: Long) = SessionEntity(
        id = id,
        vehicleProfileId = 1L,
        startedAt = 1_000L,
        endedAt = 601_000L,
        adapterName = "Vlink",
        maxRpm = 0,
        maxSpeed = 0,
        distanceKm = 12.5f,
    )

    @Test
    fun `finalizar_viaje cierra con motivo MCP y resume duracion y distancia`() = runTest {
        val m = manager()
        coEvery { m.finalizarViajeManteniendoEnlace(MotivoFin.MCP) } returns 7L
        coEvery { m.sesion(7L) } returns sesion(7L)

        val json = JSONObject(FinalizarViajeTool(m).call(JSONObject()))

        assertEquals(7L, json.getLong("cerrado"))
        assertEquals(600L, json.getLong("duracionS"))
        assertEquals(12.5, json.getDouble("distanciaKm"), 0.001)
        assertEquals("conectado", json.getString("enlace"))
    }

    @Test
    fun `finalizar_viaje sin viaje es idempotente`() = runTest {
        val m = manager()
        coEvery { m.finalizarViajeManteniendoEnlace(MotivoFin.MCP) } returns null

        val json = JSONObject(FinalizarViajeTool(m).call(JSONObject()))

        assertTrue(json.isNull("cerrado"))
        assertEquals("conectado", json.getString("enlace"))
    }

    @Test
    fun `finalizar_viaje sin adaptador no toca el viaje`() = runTest {
        val m = manager(ConnectionState.Disconnected)

        val json = JSONObject(FinalizarViajeTool(m).call(JSONObject()))

        assertFalse(json.getBoolean("conectado"))
        coVerify(exactly = 0) { m.finalizarViajeManteniendoEnlace(any()) }
    }

    @Test
    fun `iniciar_viaje devuelve el id o el motivo del rechazo`() = runTest {
        val m = manager()
        coEvery { m.iniciarViajeSobreEnlace() } returns Result.success(9L) andThen
            Result.failure(ViajeNoDisponibleException("Ya hay un viaje en curso"))
        val tool = IniciarViajeTool(m)

        assertEquals(9L, JSONObject(tool.call(JSONObject())).getLong("id"))
        assertEquals("Ya hay un viaje en curso", JSONObject(tool.call(JSONObject())).getString("error"))
    }

    @Test
    fun `las tools de viaje piden permiso de control`() {
        assertEquals(McpPermiso.CONTROL, FinalizarViajeTool(manager()).permiso)
        assertEquals(McpPermiso.CONTROL, IniciarViajeTool(manager()).permiso)
    }
}
