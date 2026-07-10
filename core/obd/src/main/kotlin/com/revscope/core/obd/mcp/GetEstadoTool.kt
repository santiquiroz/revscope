package com.revscope.core.obd.mcp

import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONObject
import javax.inject.Inject

/** Estado actual: conexión, perfil activo y lecturas en vivo. */
class GetEstadoTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "get_estado"
    override val description = "Estado actual del vehículo: conexión, perfil activo y lecturas en vivo"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        val state = sessionManager.connectionState.value
        val profile = sessionManager.activeProfile.value
        return JSONObject()
            .put("conexion", conexionTexto(state))
            .put("adaptador", (state as? ConnectionState.Connected)?.deviceName ?: JSONObject.NULL)
            .put("viajeGpsActivo", sessionManager.isGpsSessionActive.value)
            .put("perfilActivo", profile?.let(::perfilJson) ?: JSONObject.NULL)
            .put("lecturasEnVivo", lecturasJson(sessionManager))
            .toString()
    }

    private fun conexionTexto(state: ConnectionState): String = when (state) {
        is ConnectionState.Connected -> "conectado"
        ConnectionState.Connecting -> "conectando"
        is ConnectionState.Error -> "error"
        ConnectionState.Disconnected -> "desconectado"
    }

    private fun perfilJson(profile: VehicleProfileEntity): JSONObject =
        JSONObject()
            .put("nombre", profile.name)
            .put("tipo", profile.type)
            .put("combustible", profile.fuelType)

    private fun lecturasJson(sessionManager: ObdSessionManager): JSONObject =
        JSONObject().apply {
            sessionManager.readings.value.forEach { (pid, reading) ->
                put(pid, JSONObject().put("valor", reading.value).put("unidad", reading.unit))
            }
        }
}
