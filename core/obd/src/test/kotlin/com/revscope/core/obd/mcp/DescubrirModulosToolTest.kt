package com.revscope.core.obd.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DescubrirModulosToolTest {

    @Test
    fun `sin conexion retorna mensaje de vehiculo no conectado`() = runTest {
        val fixture = leaseFixture(false)

        val json = JSONObject(DescubrirModulosTool(fixture.manager).call(JSONObject()))

        assertEquals(false, json.getBoolean("conectado"))
        assertEquals("vehículo no conectado", json.getString("mensaje"))
    }

    @Test
    fun `un NRC de modo 22 tambien confirma que el modulo esta presente`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("AT DPN\r", any()) } returns "A6\r>"
        coEvery { fixture.transport.targetedExchange(any(), "22 F190", 400L) } returns "NO DATA\r>"
        coEvery { fixture.transport.targetedExchange("7E0", "22 F190", 400L) } returns "7E8 03 7F 22 31\r>"

        val json = JSONObject(DescubrirModulosTool(fixture.manager).call(JSONObject()))

        assertEquals("A6", json.getString("protocolo"))
        assertTrue(json.getJSONArray("modulos").getJSONObject(0).getBoolean("presente"))
        assertEquals("7E0", json.getJSONArray("modulos").getJSONObject(0).getString("header"))
        assertEquals(22, json.getInt("sondeados"))
        coVerify(exactly = 0) { fixture.transport.targetedExchange("7DF", any(), any()) }
    }

    @Test
    fun `protocolo no CAN devuelve error sin sondear`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("AT DPN\r", any()) } returns "7\r>"

        val json = JSONObject(DescubrirModulosTool(fixture.manager).call(JSONObject()))

        assertTrue(json.getString("error").contains("11 bits"))
        coVerify(exactly = 0) { fixture.transport.targetedExchange(any(), any(), any()) }
    }
}
