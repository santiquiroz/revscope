package com.revscope.core.obd.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class McpServerServiceTest {

    @Test
    fun `servidor activo muestra url local`() {
        val text = McpServerService.notificationTextFor(
            McpServerState.Running("http://192.168.1.20:8765/mcp"),
        )

        assertEquals("Servidor MCP activo en http://192.168.1.20:8765/mcp", text)
    }

    @Test
    fun `sin wifi pide conectar a una red`() {
        val text = McpServerService.notificationTextFor(McpServerState.NoWifi)

        assertEquals("Sin WiFi — conecta a una red para activar el servidor MCP", text)
    }

    @Test
    fun `servidor detenido muestra error de inicio`() {
        val text = McpServerService.notificationTextFor(McpServerState.Stopped)

        assertEquals("No se pudo iniciar el servidor MCP", text)
    }
}
