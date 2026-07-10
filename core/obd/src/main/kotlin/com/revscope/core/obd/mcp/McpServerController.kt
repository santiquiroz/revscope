package com.revscope.core.obd.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * Settings screen so the URL only appears once the server is actually bound to a WiFi IP. While
 * running, a background watchdog re-checks the bound WiFi IP every [wifiCheckIntervalMs] and
 * stops the server — publishing [McpServerState.NoWifi] — the moment it changes or disappears,
 * so a stale MCP URL never keeps answering on a network the phone already left.
 */
@Singleton
class McpServerController {

    private val dispatcher: McpDispatcher
    private val networkAddress: McpNetworkAddress
    private val port: Int
    private val wifiCheckIntervalMs: Long

    @Inject
    constructor(
        dispatcher: McpDispatcher,
        networkAddress: McpNetworkAddress,
    ) : this(dispatcher, networkAddress, MCP_PORT, WIFI_CHECK_INTERVAL_MS)

    internal constructor(
        dispatcher: McpDispatcher,
        networkAddress: McpNetworkAddress,
        port: Int,
        wifiCheckIntervalMs: Long = WIFI_CHECK_INTERVAL_MS,
    ) {
        this.dispatcher = dispatcher
        this.networkAddress = networkAddress
        this.port = port
        this.wifiCheckIntervalMs = wifiCheckIntervalMs
    }

    private val _state = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    val state: StateFlow<McpServerState> = _state.asStateFlow()

    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var server: McpHttpServer? = null
    private var wifiWatchdogJob: Job? = null

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
            startWifiWatchdog(ip)
            true
        } catch (e: Exception) {
            Timber.w(e, "McpServerController: failed to start MCP server")
            _state.value = McpServerState.Stopped
            false
        }
    }

    fun stop() {
        cancelWifiWatchdog()
        server?.stop()
        server = null
        _state.value = McpServerState.Stopped
    }

    /** Polls [McpNetworkAddress] until the bound IP changes or WiFi disappears, then stops. */
    private fun startWifiWatchdog(boundIp: String) {
        cancelWifiWatchdog()
        wifiWatchdogJob = watchdogScope.launch {
            while (true) {
                delay(wifiCheckIntervalMs)
                if (networkAddress.currentWifiIpv4() != boundIp) {
                    Timber.i("McpServerController: WiFi changed or lost — stopping MCP server")
                    stopForWifiLoss()
                    break
                }
            }
        }
    }

    private fun cancelWifiWatchdog() {
        wifiWatchdogJob?.cancel()
        wifiWatchdogJob = null
    }

    private fun stopForWifiLoss() {
        server?.stop()
        server = null
        _state.value = McpServerState.NoWifi
    }

    companion object {
        const val MCP_PORT = 8765
        const val MCP_PATH = "/mcp"
        private const val START_TIMEOUT_MS = 5_000
        private const val WIFI_CHECK_INTERVAL_MS = 60_000L
    }
}
