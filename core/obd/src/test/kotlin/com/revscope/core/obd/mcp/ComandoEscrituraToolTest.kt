package com.revscope.core.obd.mcp

import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class ComandoEscrituraToolTest {

    private val ejecutor = mockk<EjecutorEscritura>()
    private val tool = ComandoEscrituraTool(ejecutor)

    @Test
    fun `bloquea las transferencias de programación antes de ejecutar`() = runTest {
        val json = JSONObject(
            tool.call(
                JSONObject()
                    .put("pasos", JSONArray().put("10 02"))
                    .put("descripcion", "programar módulo"),
            ),
        )

        assertTrue(json.getString("error").contains("bloqueado"))
        assertTrue(json.getString("error").contains("Bluetooth"))
        coVerify(exactly = 0) { ejecutor.ejecutar(any(), any()) }
    }

    @Test
    fun `rechaza comandos AT con mensaje de uso`() = runTest {
        val json = JSONObject(
            tool.call(
                JSONObject()
                    .put("pasos", JSONArray().put("AT H1"))
                    .put("descripcion", "prueba"),
            ),
        )

        assertTrue(json.getString("error").contains("los comandos AT no van por aquí"))
        coVerify(exactly = 0) { ejecutor.ejecutar(any(), any()) }
    }
}
