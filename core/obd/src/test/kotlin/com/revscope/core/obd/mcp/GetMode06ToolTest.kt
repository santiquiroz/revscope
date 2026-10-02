package com.revscope.core.obd.mcp

import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetMode06ToolTest {

    @Test
    fun `sin conexion retorna mensaje de vehiculo no conectado`() = runTest {
        val fixture = leaseFixture(false)

        val json = JSONObject(GetMode06Tool(fixture.manager).call(JSONObject()))

        assertEquals(false, json.getBoolean("conectado"))
        assertEquals("vehículo no conectado", json.getString("mensaje"))
    }

    @Test
    fun `lee soporte y decodifica resultado con limites`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("06 00\r", any()) } returns "4600 80 00 00 00\r>"
        coEvery { fixture.transport.exchange("06 01\r", any()) } returns "4601010100050000000A\r>"

        val json = JSONObject(GetMode06Tool(fixture.manager).call(JSONObject()))
        val prueba = json.getJSONArray("pruebas").getJSONObject(0)

        assertTrue(json.getBoolean("soportado"))
        assertEquals("01", prueba.getString("mid"))
        assertEquals(1, prueba.getInt("tid"))
        assertEquals(5.0, prueba.getDouble("valor"), 0.0)
        assertTrue(prueba.getBoolean("pasa"))
    }

    @Test
    fun `NO DATA informa que la ECU no soporta modo 06`() = runTest {
        val fixture = leaseFixture()
        coEvery { fixture.transport.exchange("06 00\r", any()) } returns "NO DATA\r>"

        val json = JSONObject(GetMode06Tool(fixture.manager).call(JSONObject()))

        assertEquals(false, json.getBoolean("soportado"))
        assertEquals("la ECU no soporta el modo 06", json.getString("mensaje"))
        coVerify(exactly = 1) { fixture.transport.exchange("06 00\r", any()) }
    }
}
