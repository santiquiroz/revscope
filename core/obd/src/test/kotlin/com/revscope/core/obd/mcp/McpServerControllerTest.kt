package com.revscope.core.obd.mcp

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerControllerTest {

    @Test
    fun `state inicia detenido`() = runTest {
        val controller = controller()

        assertEquals(McpServerState.Stopped, controller.state.value)
    }

    @Test
    fun `start sin ipv4 wifi deja estado no wifi y retorna false`() = runTest {
        val networkAddress = mockk<McpNetworkAddress>()
        every { networkAddress.currentWifiIpv4() } returns null
        val controller = controller(networkAddress)

        val started = controller.start("token")

        assertEquals(false, started)
        assertEquals(McpServerState.NoWifi, controller.state.value)
    }

    @Test
    fun `start con ipv4 wifi inicia servidor y publica url mcp`() = runTest {
        val networkAddress = mockk<McpNetworkAddress>()
        every { networkAddress.currentWifiIpv4() } returns "127.0.0.1"
        val controller = controller(networkAddress)

        try {
            val started = controller.start("token")

            assertTrue(started)
            val state = controller.state.value
            assertTrue(state is McpServerState.Running)
            val url = (state as McpServerState.Running).url
            assertEquals("127.0.0.1", URI(url).host)
            assertTrue(URI(url).port > 0)
            assertEquals(McpServerController.MCP_PATH, URI(url).path)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `start repetido mientras esta corriendo es idempotente`() = runTest {
        val networkAddress = mockk<McpNetworkAddress>()
        every { networkAddress.currentWifiIpv4() } returns "127.0.0.1"
        val controller = controller(networkAddress)

        try {
            val firstStarted = controller.start("token")
            val secondStarted = controller.start("otro-token")

            assertTrue(firstStarted)
            assertTrue(secondStarted)
            verify(exactly = 1) { networkAddress.currentWifiIpv4() }
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `stop despues de iniciar deja estado detenido`() = runTest {
        val networkAddress = mockk<McpNetworkAddress>()
        every { networkAddress.currentWifiIpv4() } returns "127.0.0.1"
        val controller = controller(networkAddress)

        try {
            assertTrue(controller.start("token"))

            controller.stop()

            assertEquals(McpServerState.Stopped, controller.state.value)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `stop sin iniciar es no op seguro`() = runTest {
        val controller = controller()

        controller.stop()

        assertEquals(McpServerState.Stopped, controller.state.value)
    }

    @Test
    fun `wifi perdido durante ejecucion detiene el servidor y publica no wifi`() = runBlocking {
        val networkAddress = mockk<McpNetworkAddress>()
        every { networkAddress.currentWifiIpv4() } returnsMany listOf("127.0.0.1", null)
        val controller = controller(networkAddress, wifiCheckIntervalMs = 20L)

        try {
            assertTrue(controller.start("token"))

            withTimeout(3_000) {
                while (controller.state.value !is McpServerState.NoWifi) delay(10)
            }

            assertEquals(McpServerState.NoWifi, controller.state.value)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun `wifi que cambia de ip durante ejecucion detiene el servidor`() = runBlocking {
        val networkAddress = mockk<McpNetworkAddress>()
        every { networkAddress.currentWifiIpv4() } returnsMany listOf("127.0.0.1", "10.0.0.5")
        val controller = controller(networkAddress, wifiCheckIntervalMs = 20L)

        try {
            assertTrue(controller.start("token"))

            withTimeout(3_000) {
                while (controller.state.value !is McpServerState.NoWifi) delay(10)
            }

            assertEquals(McpServerState.NoWifi, controller.state.value)
        } finally {
            controller.stop()
        }
    }

    private fun controller(
        networkAddress: McpNetworkAddress = mockk(relaxed = true),
        wifiCheckIntervalMs: Long = 60_000L,
    ): McpServerController =
        McpServerController(
            dispatcher = McpDispatcher(emptyList()),
            networkAddress = networkAddress,
            port = 0,
            wifiCheckIntervalMs = wifiCheckIntervalMs,
        )
}
