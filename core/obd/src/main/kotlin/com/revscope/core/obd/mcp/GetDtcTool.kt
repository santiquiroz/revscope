package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Códigos DTC activos leídos en vivo (Mode 03) — requiere adaptador conectado.
 *
 * The ECU read itself (not the connection/estado checks) is rate-limited to one call per
 * [RATE_LIMIT_WINDOW_MS] and refused outright while a trip is recording, so a chatty MCP client
 * can't hammer the transport mutex and starve the telemetry scheduler.
 */
class GetDtcTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    /** Test-only clock override — see [McpServerController] for the same internal-constructor pattern. */
    internal constructor(sessionManager: ObdSessionManager, nowMs: () -> Long) : this(sessionManager) {
        this.nowMs = nowMs
    }

    override val name = "get_dtc"
    override val description = "Códigos de falla (DTC) activos leídos en vivo del vehículo — requiere adaptador conectado"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    private var nowMs: () -> Long = { System.currentTimeMillis() }
    private var cachedResult: String? = null
    private var cachedAtMs: Long = 0L

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) {
            return JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()
        }
        if (sessionManager.currentSessionId.value != null) return tripActiveError()
        return cachedResultWithinRateLimitWindow() ?: readAndCacheDtc()
    }

    private fun tripActiveError(): String =
        JSONObject()
            .put("error", "vehículo en marcha — consulta DTC no disponible durante un viaje para no interferir con la telemetría")
            .toString()

    private fun cachedResultWithinRateLimitWindow(): String? {
        val cached = cachedResult ?: return null
        if (nowMs() - cachedAtMs >= RATE_LIMIT_WINDOW_MS) return null
        return JSONObject(cached).put("cache", true).toString()
    }

    private suspend fun readAndCacheDtc(): String {
        val result = sessionManager.readActiveDtc().fold(
            onSuccess = { codes ->
                JSONObject()
                    .put("conectado", true)
                    .put("codigos", JSONArray(codes.map { it.code }))
                    .toString()
            },
            onFailure = { e ->
                JSONObject().put("conectado", true).put("error", e.message ?: "no se pudo leer DTC").toString()
            },
        )
        cachedResult = result
        cachedAtMs = nowMs()
        return result
    }

    private companion object {
        const val RATE_LIMIT_WINDOW_MS = 30_000L
    }
}
