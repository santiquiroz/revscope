package com.revscope.core.obd.mcp

import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

private const val DEFAULT_LIMIT = 5

/** Últimos N viajes del perfil activo, con sus estadísticas agregadas. */
class GetViajesTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val sessionDao: SessionDao,
) : McpTool {

    override val name = "get_viajes"
    override val description = "Últimos viajes del vehículo activo con sus estadísticas (distancia, velocidad, eco score)"
    override val inputSchema: JSONObject = McpSchemas.optionalInt("limit", "Cantidad máxima de viajes a devolver (default 5)")

    override suspend fun call(arguments: JSONObject): String {
        val profile = sessionManager.activeProfile.value
            ?: return JSONObject().put("error", "sin perfil de vehículo activo").toString()
        val limit = arguments.optInt("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val sessions = sessionDao.getRecentForProfile(profile.id, limit)
        return JSONObject().put("viajes", JSONArray(sessions.map(::viajeJson))).toString()
    }

    private fun viajeJson(session: SessionEntity): JSONObject = JSONObject()
        .put("id", session.id)
        .put("inicio", session.startedAt)
        .put("fin", session.endedAt ?: JSONObject.NULL)
        .put("distanciaKm", session.distanceKm)
        .put("velocidadMaxKmh", session.maxSpeed)
        .put("ecoScore", session.ecoScore ?: JSONObject.NULL)
        .put("fuente", session.adapterName)

    private companion object {
        const val MAX_LIMIT = 50
    }
}
