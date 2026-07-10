package com.revscope.core.obd.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class McpServerState {
    object Stopped : McpServerState()
    data class Running(val url: String) : McpServerState()
    object NoWifi : McpServerState()
}

/**
 * Owns the [McpHttpServer] lifecycle — started/stopped by [McpServerService], observed by the
 * Settings screen so the URL only appears once the server is actually bound to a WiFi IP.
 */
@Singleton
class McpServerController {

    private val dispatcher: McpDispatcher
    private val networkAddress: McpNetworkAddress
    private val port: Int

    @Inject
    constructor(
        dispatcher: McpDispatcher,
        networkAddress: McpNetworkAddress,
    ) : this(dispatcher, networkAddress, MCP_PORT)

    internal constructor(
        dispatcher: McpDispatcher,
        networkAddress: McpNetworkAddress,
        port: Int,
    ) {
        this.dispatcher = dispatcher
        this.networkAddress = networkAddress
        this.port = port
    }

    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    val state: StateFlow<McpServerState> = _state.asStateFlow()

    private var server: McpHttpServer? = null

    /** Idempotent — returns true if a server is already (or now) running. */
    fun start(token: String): Boolean {
        if (server != null) return true
        val ip = networkAddress.currentWifiIpv4()
        if (ip == null) {
            _state.value = McpServerState.NoWifi
            return false
        }
        return try {
            val instance = McpHttpServer(ip, port, dispatcher, token)
            instance.start(START_TIMEOUT_MS, false)
            server = instance
            _state.value = McpServerState.Running("http://$ip:${instance.listeningPort}$MCP_PATH")
            true
        } catch (e: Exception) {
            Timber.w(e, "McpServerController: failed to start MCP server")
            _state.value = McpServerState.Stopped
            false
        }
    }

    fun stop() {
        server?.stop()
        server = null
        _state.value = McpServerState.Stopped
    }

    companion object {
        const val MCP_PORT = 8765
        const val MCP_PATH = "/mcp"
        private const val START_TIMEOUT_MS = 5_000
    }
}
