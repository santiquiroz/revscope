package com.revscope.core.obd.connection

import kotlinx.coroutines.flow.Flow

interface Transport {
    val isConnected: Boolean
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()

    /** Sends an AT command or OBD-II PID request (must include trailing '\r'). */
    suspend fun send(command: String)

    /** Reads the adapter response until the '>' prompt or timeout. */
    suspend fun receive(): String

    fun observeConnectionState(): Flow<ConnectionState>
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
