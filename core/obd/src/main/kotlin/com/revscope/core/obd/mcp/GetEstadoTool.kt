package com.revscope.core.obd.mcp

import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Estado actual: conexión, viaje, permisos MCP, perfil activo y lecturas en vivo con su edad. */
class GetEstadoTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val permisos: McpPermisosProvider,
) : McpTool {

    internal constructor(
        sessionManager: ObdSessionManager,
        permisos: McpPermisosProvider,
        nowMs: () -> Long,
    ) : this(sessionManager, permisos) {
        this.nowMs = nowMs
    }

    override val name = "get_estado"
    override val description =
        "Estado actual del vehículo: conexión, viaje (grabando / sin viaje), permisos del MCP, perfil activo " +
            "y lecturas en vivo con su edad en ms"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    private var nowMs: () -> Long = { System.currentTimeMillis() }

    override suspend fun call(arguments: JSONObject): String {
        val state = sessionManager.connectionState.value
        val profile = sessionManager.activeProfile.value
        return JSONObject()
            .put("conexion", conexionTexto(state))
            .put("adaptador", (state as? ConnectionState.Connected)?.deviceName ?: JSONObject.NULL)
            .put("viaje", viajeJson(sessionManager.estadoViaje.value))
            .put("viajeGpsActivo", sessionManager.isGpsSessionActive.value)
            .put("permisos", JSONArray(permisos.actuales().map { it.name.lowercase() }.sorted()))
            .put("perfilActivo", profile?.let(::perfilJson) ?: JSONObject.NULL)
            .put("lecturasEnVivo", lecturasJson())
            .toString()
    }

    private fun conexionTexto(state: ConnectionState): String = when (state) {
        is ConnectionState.Connected -> "conectado"
        ConnectionState.Connecting -> "conectando"
        is ConnectionState.Error -> "error"
        ConnectionState.Disconnected -> "desconectado"
    }

    private fun viajeJson(estado: EstadoViaje): JSONObject {
        val json = JSONObject().put("estado", DtcScanJson.estadoViaje(estado))
        if (estado is EstadoViaje.Grabando) json.put("id", estado.sessionId).put("inicioMs", estado.inicioMs)
        return json
    }

    private fun perfilJson(profile: VehicleProfileEntity): JSONObject =
        JSONObject()
            .put("nombre", profile.name)
            .put("tipo", profile.type)
            .put("combustible", profile.fuelType)

    private fun lecturasJson(): JSONObject {
        val ahora = nowMs()
        return JSONObject().apply {
            sessionManager.readings.value.forEach { (pid, reading) ->
                put(
                    pid,
                    JSONObject()
                        .put("valor", reading.value)
                        .put("unidad", reading.unit)
                        .put("edadMs", (ahora - reading.timestamp).coerceAtLeast(0)),
                )
            }
        }
    }
}
