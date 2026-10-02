package com.revscope.core.obd.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetInfoEcuToolTest {

    @Test
    fun `sin conexion retorna mensaje de vehiculo no conectado`() = runTest {
        val fixture = leaseFixture(conectado = false)

        val json = JSONObject(GetInfoEcuTool(fixture.manager).call(JSONObject()))

        assertEquals(false, json.getBoolean("conectado"))
        assertEquals("vehículo no conectado", json.getString("mensaje"))
        coVerify(exactly = 0) { fixture.manager.withDiagnosticLease<Any?>(any(), any(), any()) }
    }

    @Test
    fun `lee identificacion ECU y mantiene lecturas independientes`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("09 02\r", any()) } returns "4902013147314A43353434303030303030303030\r"
        coEvery { fixture.transport.exchange("09 04\r", any()) } returns "4904014142434445464748494A4B4C4D4E4F50\r"
        coEvery { fixture.transport.exchange("09 06\r", any()) } returns "49060112345678\r"
        coEvery { fixture.transport.exchange("09 0A\r", any()) } returns "490A0145435531\r"
        coEvery { fixture.transport.exchange("AT DPN\r", any()) } returns "A6\r>"
        coEvery { fixture.transport.exchange("AT RV\r", any()) } returns "12.4V\r>"

        val json = JSONObject(GetInfoEcuTool(fixture.manager).call(JSONObject()))

        assertEquals("1G1JC544000000000", json.getString("vin"))
        assertEquals("ABCDEFGHIJKLMNOP", json.getJSONArray("calibraciones").getString(0))
        assertEquals("A6", json.getString("protocolo"))
        assertEquals("12.4V", json.getString("voltajeAdaptador"))
        assertEquals("12345678", json.getJSONArray("cvn").getString(0))
        assertEquals("ECU1", json.getString("nombreEcu"))
        assertEquals(0, json.getJSONArray("errores").length())
    }

    @Test
    fun `NRC de una identificacion se registra sin abortar otras consultas`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("09 02\r", any()) } returns "7F 09 31\r>"
        coEvery { fixture.transport.exchange(any(), any()) } returns "NO DATA\r>"
        coEvery { fixture.transport.exchange("09 02\r", any()) } returns "7F 09 31\r>"
        coEvery { fixture.transport.exchange("AT DPN\r", any()) } returns "6\r>"
        coEvery { fixture.transport.exchange("AT RV\r", any()) } returns "12.4V\r>"

        val json = JSONObject(GetInfoEcuTool(fixture.manager).call(JSONObject()))

        assertTrue(json.isNull("vin"))
        assertTrue(json.getJSONArray("errores").getJSONObject(0).getString("mensaje").contains("NRC"))
        coVerify(exactly = 1) { fixture.transport.exchange("09 04\r", any()) }
        coVerify(exactly = 1) { fixture.transport.exchange("AT RV\r", any()) }
    }
}
