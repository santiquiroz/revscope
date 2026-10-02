package com.revscope.core.obd.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeerDidToolTest {

    private fun args(did: String = "F190") = JSONObject().put("dids", JSONArray().put(did))

    @Test
    fun `sin conexion retorna mensaje de vehiculo no conectado`() = runTest {
        val fixture = leaseFixture(false)

        val json = JSONObject(LeerDidTool(fixture.manager).call(args()))

        assertEquals(false, json.getBoolean("conectado"))
        assertEquals("vehículo no conectado", json.getString("mensaje"))
    }

    @Test
    fun `lee DID y devuelve ASCII de los datos positivos`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("22 F1 90\r", any()) } returns "62 F1 90 48 45 4C 4C 4F\r>"

        val json = JSONObject(LeerDidTool(fixture.manager).call(args()))

        assertEquals("HELLO", json.getJSONArray("resultados").getJSONObject(0).getString("ascii"))
        assertEquals("positiva", json.getJSONArray("resultados").getJSONObject(0)
            .getJSONArray("respuestas").getJSONObject(0).getString("tipo"))
    }

    @Test
    fun `NRC es serializado y DID invalido se rechaza antes del lease`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("22 F1 90\r", any()) } returns "7F 22 31\r>"

        val json = JSONObject(LeerDidTool(fixture.manager).call(args()))
        val invalid = JSONObject(LeerDidTool(fixture.manager).call(args("F19")))

        assertEquals("negativa", json.getJSONArray("resultados").getJSONObject(0)
            .getJSONArray("respuestas").getJSONObject(0).getString("tipo"))
        assertTrue(invalid.has("error"))
        coVerify(exactly = 1) { fixture.manager.withDiagnosticLease<Any?>(any(), any(), any()) }
    }

    @Test
    fun `rechaza header que no sea CAN de 11 bits`() = runTest {
        val fixture = leaseFixture()

        val json = JSONObject(LeerDidTool(fixture.manager).call(args().put("header", "7DF0")))

        assertTrue(json.getString("error").contains("header"))
        coVerify(exactly = 0) { fixture.manager.withDiagnosticLease<Any?>(any(), any(), any()) }
    }
}
