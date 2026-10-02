package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import com.revscope.core.obd.mcp.escritura.OrigenAutorizacion
import com.revscope.core.obd.mcp.escritura.ResultadoEscritura
import com.revscope.core.obd.mcp.escritura.SolicitudEscritura
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReiniciarEcuToolTest {

    private val ejecutor = mockk<EjecutorEscritura>()
    private val bt = mockk<Transport>()
    private val tool = ReiniciarEcuTool(ejecutor)

    @Test
    fun `requiere motor apagado y usa el reinicio suave predeterminado`() = runTest {
        val solicitud = slot<SolicitudEscritura>()
        every { bt.isConnected } returns true
        coEvery { bt.targetedExchange("7E0", "11 03", any()) } returns "7E8025103\r"
        coEvery { ejecutor.ejecutar(capture(solicitud), any()) } coAnswers {
            val accion = secondArg<suspend (Transport) -> List<String>>()
            ResultadoEscritura.Ejecutada(OrigenAutorizacion.TOQUE, accion(bt))
        }

        val json = JSONObject(tool.call(JSONObject()))

        assertTrue(solicitud.captured.requiereMotorApagado)
        assertEquals(listOf("11 03"), solicitud.captured.pasos)
        assertEquals("la ECU 7E0 aceptó el reinicio (suave)", json.getString("mensaje"))
    }

    @Test
    fun `tipo fuera del enum no llega al ejecutor`() = runTest {
        val json = JSONObject(tool.call(JSONObject().put("tipo", "forzado")))

        assertTrue(json.has("error"))
        coVerify(exactly = 0) { ejecutor.ejecutar(any(), any()) }
    }
}
