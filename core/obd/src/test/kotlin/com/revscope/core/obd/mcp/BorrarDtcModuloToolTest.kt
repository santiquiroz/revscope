package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import com.revscope.core.obd.mcp.escritura.OrigenAutorizacion
import com.revscope.core.obd.mcp.escritura.ResultadoEscritura
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorrarDtcModuloToolTest {

    private val ejecutor = mockk<EjecutorEscritura>()
    private val bt = mockk<Transport>()
    private val tool = BorrarDtcModuloTool(ejecutor)

    @Test
    fun `rechazo del dueño se devuelve sin ocultar el motivo`() = runTest {
        coEvery { ejecutor.ejecutar(any(), any()) } returns
            ResultadoEscritura.NoAutorizada("el dueño rechazó la escritura")

        val json = JSONObject(tool.call(JSONObject().put("header", "7E0")))

        assertFalse(json.getBoolean("ejecutada"))
        assertEquals("el dueño rechazó la escritura", json.getString("motivo"))
        coVerify(exactly = 0) { bt.targetedExchange(any(), any(), any()) }
    }

    @Test
    fun `header inválido se rechaza antes de pedir autorización`() = runTest {
        val json = JSONObject(tool.call(JSONObject().put("header", "7E00")))

        assertTrue(json.getString("error").contains("header"))
        coVerify(exactly = 0) { ejecutor.ejecutar(any(), any()) }
    }

    @Test
    fun `usa el grupo predeterminado y el intercambio dirigido`() = runTest {
        every { bt.isConnected } returns true
        coEvery { bt.targetedExchange("7E0", "14 FF FF FF", any()) } returns "7E80154\r"
        coEvery { ejecutor.ejecutar(any(), any()) } coAnswers {
            val accion = secondArg<suspend (Transport) -> List<String>>()
            ResultadoEscritura.Ejecutada(OrigenAutorizacion.TOQUE, accion(bt))
        }

        val json = JSONObject(tool.call(JSONObject().put("header", "7e0")))

        assertEquals("7E0", json.getString("header"))
        assertEquals("códigos del módulo 7E0 borrados", json.getString("mensaje"))
    }
}
