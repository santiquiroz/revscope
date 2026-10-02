package com.revscope.core.obd.mcp

import com.revscope.core.obd.catalogo.CatalogoPropietario
import com.revscope.core.obd.catalogo.CodecCatalogo
import com.revscope.core.obd.catalogo.EntradaCatalogo
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.diagnostics.uds.PasoUds
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
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.rules.TemporaryFolder

class LeerCatalogoToolTest {
    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `lee DID dirigido y calcula la formula con datos sin eco`() = runTest {
        val manager = mockk<ObdSessionManager>()
        val transporte = mockk<Transport>()
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connected("Vlink"))
        coEvery { transporte.targetedExchange("7E0", "22 21 21", any()) } returns "7E8056221210041\r"
        coEvery { manager.withDiagnosticLease<List<PasoUds>>(any(), any(), any()) } coAnswers {
            Result.success(thirdArg<suspend (Transport) -> List<PasoUds>>().invoke(transporte))
        }

        val json = JSONObject(tool(manager).call(JSONObject().put("id", "test.id")))

        assertEquals(65.0, json.getDouble("valor"), 0.0)
        assertEquals("00 41", json.getString("datosHex"))
        assertTrue(json.getJSONArray("pasos").getJSONObject(0).getBoolean("exitoso"))
    }

    @Test
    fun `rechaza operaciones de escritura antes de pedir lease`() = runTest {
        val manager = mockk<ObdSessionManager>()
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connected("Vlink"))
        val entrada = entry().copy(tipo = "escritura", pasos = listOf("2E F1 90"))
        val catalogo = CatalogoPropietario({ CodecCatalogo.escribir(null, listOf(entrada)) }, java.io.File(temporal.root, "catalogo_usuario.json"))

        val json = JSONObject(LeerCatalogoTool(catalogo, manager).call(JSONObject().put("id", entrada.id)))

        assertTrue(json.getString("error").contains("ejecútala"))
        coVerify(exactly = 0) { manager.withDiagnosticLease<List<PasoUds>>(any(), any(), any()) }
    }

    @Test
    fun `sin respuesta positiva incluye mensaje`() = runTest {
        val manager = mockk<ObdSessionManager>()
        val transporte = mockk<Transport>()
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connected("Vlink"))
        coEvery { transporte.targetedExchange(any(), any(), any()) } returns "7E8037F2231\r"
        coEvery { manager.withDiagnosticLease<List<PasoUds>>(any(), any(), any()) } coAnswers {
            Result.success(thirdArg<suspend (Transport) -> List<PasoUds>>().invoke(transporte))
        }

        val json = JSONObject(tool(manager).call(JSONObject().put("id", "test.id")))

        assertFalse(json.getJSONArray("pasos").getJSONObject(0).getBoolean("exitoso"))
        assertTrue(json.getString("mensaje").contains("no respondió positivamente"))
    }

    private fun tool(manager: ObdSessionManager) =
        LeerCatalogoTool(CatalogoPropietario({ CodecCatalogo.escribir(null, listOf(entry())) }, archivoTemporal()), manager)

    private fun entry() = EntradaCatalogo(
        "test.id", "Mazda", listOf("CX-30"), null, "PCM", "7E0", "lectura",
        listOf("22 21 21"), "B", "km/h", "Velocidad", false, "bajo", null, null, false, null,
    )

    private fun archivoTemporal() = File(temporal.root, "catalogo.json")
}
