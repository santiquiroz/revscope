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

    /**
     * Atomic read-only diagnostic exchange to a specific 11-bit CAN module.
     *
     * Under the single I/O lock: enables response headers (AT H1), sets the
     * request header (AT SH), sends [request], reads the reply, then ALWAYS
     * restores the functional broadcast header (7DF) and headers-off (AT H0).
     * Restoring inside the same lock guarantees concurrent telemetry — which
     * relies on the default functional header — is never emitted under
     * [requestHeader].
     *
     * [requestHeader] is an 11-bit CAN id as 3 hex chars (e.g. "7E0", "720").
     * The reply keeps its source-header prefix (headers are on for this call),
     * so the caller can tell which module answered.
     *
     * Only valid on 11-bit CAN protocols; callers must gate on the protocol.
     * Throws [java.io.IOException] on timeout (treat as "no module answered").
     */
    suspend fun targetedExchange(requestHeader: String, request: String, timeoutMs: Long): String

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
