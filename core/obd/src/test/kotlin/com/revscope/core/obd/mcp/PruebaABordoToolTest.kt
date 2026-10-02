package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import com.revscope.core.obd.session.ObdSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PruebaABordoToolTest {

    private val ejecutor = mockk<EjecutorEscritura>()
    private val manager = mockk<ObdSessionManager>()
    private val bt = mockk<Transport>()
    private val tool = PruebaABordoTool(manager, ejecutor)

    @Test
    fun `sin tid consulta el mapa de pruebas en modo lectura`() = runTest {
        coEvery { bt.exchange("08 00\r", 3_000) } returns "48 00 80 00 00 01\r"
        coEvery {
            manager.withDiagnosticLease<String>("mcp:prueba_a_bordo", any(), any())
        } coAnswers {
            val accion = thirdArg<suspend (Transport) -> String>()
            Result.success(accion(bt))
        }

        val json = JSONObject(tool.call(JSONObject()))

        assertTrue(json.getBoolean("soportado"))
        assertEquals(listOf("01", "20"), (0 until json.getJSONArray("tids").length()).map {
            json.getJSONArray("tids").getString(it)
        })
        coVerify(exactly = 0) { ejecutor.ejecutar(any(), any()) }
    }
}
