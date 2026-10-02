package com.revscope.core.obd.mcp

import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComandoLecturaToolTest {

    private fun tool(fixture: McpToolLeaseFixture) = ComandoLecturaTool(
        fixture.manager,
        PidRegistry(TestPids.load()),
    )

    private fun args(command: String) = JSONObject().put("comando", command)

    @Test
    fun `sin conexion retorna mensaje de vehiculo no conectado`() = runTest {
        val fixture = leaseFixture(false)

        val json = JSONObject(tool(fixture).call(args("01 0C")))

        assertEquals(false, json.getBoolean("conectado"))
        assertEquals("vehículo no conectado", json.getString("mensaje"))
    }

    @Test
    fun `decodifica respuesta positiva de PID`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("01 0C\r", any()) } returns "41 0C 1A F8\r>"

        val json = JSONObject(tool(fixture).call(args("01 0C")))

        assertEquals("0C", json.getJSONObject("decodificado").getString("pid"))
        assertEquals("rpm", json.getJSONObject("decodificado").getString("unidad"))
    }

    @Test
    fun `NRC se informa y rechaza escrituras y header invalido`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("22 F1 90\r", any()) } returns "7F 22 31\r>"

        val tool = tool(fixture)
        val negativa = JSONObject(tool.call(args("22 F190")))
        val escritura = JSONObject(tool.call(args("2E F1 90")))
        val header = JSONObject(tool.call(args("01 0C").put("header", "GG1")))

        assertEquals("negativa", negativa.getJSONArray("respuestas").getJSONObject(0).getString("tipo"))
        assertTrue(escritura.getString("error").contains("comando_escritura"))
        assertTrue(header.has("error"))
        coVerify(exactly = 1) { fixture.manager.withDiagnosticLease<Any?>(any(), any(), any()) }
    }

    @Test
    fun `AT no admite header`() = runTest {
        val fixture = leaseFixture()

        val json = JSONObject(tool(fixture).call(args("AT RV").put("header", "7E0")))

        assertTrue(json.getString("error").contains("AT"))
    }
}
