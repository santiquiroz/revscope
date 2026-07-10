package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Códigos DTC activos leídos en vivo (Mode 03) — requiere adaptador conectado. */
class GetDtcTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "get_dtc"
    override val description = "Códigos de falla (DTC) activos leídos en vivo del vehículo — requiere adaptador conectado"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) {
            return JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()
        }
        return sessionManager.readActiveDtc().fold(
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
    }
}
