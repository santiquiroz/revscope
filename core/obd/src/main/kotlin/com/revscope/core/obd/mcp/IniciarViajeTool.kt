package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONObject
import javax.inject.Inject

/** Abre un viaje OBD nuevo sobre el adaptador ya conectado. */
class IniciarViajeTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "iniciar_viaje"
    override val description =
        "Inicia un viaje nuevo sobre el adaptador ya conectado (tras finalizar_viaje). Falla si no hay " +
            "adaptador o ya hay un viaje grabando"
    override val inputSchema: JSONObject = McpSchemas.noArguments()
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinEnlaceJson()
        return sessionManager.iniciarViajeSobreEnlace().fold(
            onSuccess = { id -> JSONObject().put("id", id).put("estado", "grabando") },
            onFailure = { e -> JSONObject().put("error", e.message ?: "no se pudo iniciar el viaje") },
        ).toString()
    }
}
