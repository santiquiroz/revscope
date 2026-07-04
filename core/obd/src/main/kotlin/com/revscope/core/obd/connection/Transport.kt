package com.revscope.core.obd.connection

import kotlinx.coroutines.flow.Flow

interface Transport {
    val isConnected: Boolean
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()

    /** Sends an AT command or OBD-II PID request (must include trailing '\r'). */
    suspend fun send(command: String)

    /**
     * Reads the adapter response until the '>' prompt.
     * Throws [java.io.IOException] if the stream closes or [timeoutMs] elapses
     * before the prompt arrives.
     */
    suspend fun receive(timeoutMs: Long = DEFAULT_READ_TIMEOUT_MS): String

    /**
     * Serialized send+receive pair. ELM327 is half-duplex — all callers must go
     * through this method so concurrent requests never interleave on the wire.
     * Drains stale bytes left by a previous timed-out read before sending.
     */
    suspend fun exchange(command: String, timeoutMs: Long = DEFAULT_READ_TIMEOUT_MS): String

    fun observeConnectionState(): Flow<ConnectionState>

    companion object {
        const val DEFAULT_READ_TIMEOUT_MS = 3_000L
    }
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
