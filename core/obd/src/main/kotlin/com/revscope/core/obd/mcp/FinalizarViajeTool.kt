package com.revscope.core.obd.mcp

import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.MotivoFin
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONObject
import javax.inject.Inject

/** Cierra el viaje OBD en curso y deja el adaptador conectado. Idempotente. */
class FinalizarViajeTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "finalizar_viaje"
    override val description =
        "Finaliza el viaje en curso y deja el adaptador conectado (el sondeo sigue y get_dtc responde). " +
            "Idempotente: sin viaje OBD en curso no hace nada"
    override val inputSchema: JSONObject = McpSchemas.noArguments()
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinEnlaceJson()
        val cerrado = sessionManager.finalizarViajeManteniendoEnlace(MotivoFin.MCP)
            ?: return JSONObject().put("cerrado", JSONObject.NULL).put("mensaje", "no había un viaje OBD en curso")
                .put("enlace", "conectado").toString()
        return resumen(cerrado, sessionManager.sesion(cerrado)).toString()
    }

    private fun resumen(id: Long, sesion: SessionEntity?): JSONObject {
        val json = JSONObject().put("cerrado", id).put("enlace", "conectado")
        if (sesion == null) return json
        val fin = sesion.endedAt ?: sesion.startedAt
        return json
            .put("duracionS", (fin - sesion.startedAt) / 1_000)
            .put("distanciaKm", sesion.distanceKm.toDouble())
    }
}

internal fun sinEnlaceJson(): String =
    JSONObject().put("conectado", false).put("error", "sin adaptador conectado").toString()
