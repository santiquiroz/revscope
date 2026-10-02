package com.revscope.core.obd.mcp

import com.revscope.core.obd.catalogo.CatalogoPropietario
import com.revscope.core.obd.catalogo.CodecCatalogo
import com.revscope.core.obd.catalogo.EntradaCatalogo
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.diagnostics.uds.PasoUds
import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import com.revscope.core.obd.mcp.escritura.OrigenAutorizacion
import com.revscope.core.obd.mcp.escritura.ResultadoEscritura
import com.revscope.core.obd.mcp.escritura.SolicitudEscritura
import com.revscope.core.obd.session.ObdSessionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

class EjecutarCatalogoToolTest {
    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `pasa por el ejecutor y la solicitud advierte riesgo y verificacion`() = runTest {
        val manager = mockk<ObdSessionManager>()
        val ejecutor = mockk<EjecutorEscritura>()
        val transporte = mockk<Transport>()
        val solicitud = slot<SolicitudEscritura>()
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connected("Vlink"))
        coEvery { transporte.targetedExchange("7E0", "2E F1 90", any()) } returns "7E8036EF190\r"
        coEvery { ejecutor.ejecutar(capture(solicitud), any()) } coAnswers {
            val respuestas = secondArg<suspend (Transport) -> List<String>>().invoke(transporte)
            ResultadoEscritura.Ejecutada(OrigenAutorizacion.TOQUE, respuestas)
        }
        val catalogo = CatalogoPropietario(
            { CodecCatalogo.escribir(null, listOf(entry())) },
            File(temporal.root, "catalogo.json"),
        )

        val json = JSONObject(EjecutarCatalogoTool(catalogo, ejecutor).call(JSONObject().put("id", "test.id")))

        assertEquals("ejecutar_catalogo", solicitud.captured.tool)
        assertTrue(solicitud.captured.resumen.contains("SIN VERIFICAR"))
        assertTrue(solicitud.captured.requiereMotorApagado)
        assertTrue(json.getBoolean("completada"))
        assertFalse(json.getBoolean("verificado"))
    }

    @Test
    fun `rechaza una lectura y usa leer_catalogo`() = runTest {
        val entrada = entry().copy(tipo = "lectura", pasos = listOf("22 21 21"))
        val catalogo = CatalogoPropietario(
            { CodecCatalogo.escribir(null, listOf(entrada)) },
            File(temporal.root, "catalogo.json"),
        )
        val manager = mockk<ObdSessionManager>()
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connected("Vlink"))

        val json = JSONObject(EjecutarCatalogoTool(catalogo, mockk()).call(JSONObject().put("id", entrada.id)))

        assertTrue(json.getString("error").contains("usa leer_catalogo"))
    }

    private fun entry() = EntradaCatalogo(
        "test.id", "Mazda", listOf("CX-30"), null, "PCM", "7E0", "escritura",
        listOf("2E F1 90"), null, null, "Configurar módulo", false, "medio",
        "https://example.test", null, false, null,
    )
}
