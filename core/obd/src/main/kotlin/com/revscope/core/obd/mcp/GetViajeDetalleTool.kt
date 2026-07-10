package com.revscope.core.obd.mcp

import com.revscope.core.data.db.dao.SessionDao
import org.json.JSONObject
import javax.inject.Inject

/** Agregados completos de un viaje puntual por id. */
class GetViajeDetalleTool @Inject constructor(
    private val sessionDao: SessionDao,
) : McpTool {

    override val name = "get_viaje_detalle"
    override val description = "Detalle agregado de un viaje puntual por su id (distancia, velocidad, combustible, lanzamientos)"
    override val inputSchema: JSONObject = McpSchemas.requiredInt("id", "Id del viaje (ver get_viajes)")

    override suspend fun call(arguments: JSONObject): String {
        val id = arguments.optLong("id", -1L)
        if (id <= 0) return JSONObject().put("error", "id inválido").toString()
        val session = sessionDao.getById(id)
            ?: return JSONObject().put("error", "viaje no encontrado").toString()
        return JSONObject()
            .put("id", session.id)
            .put("inicio", session.startedAt)
            .put("fin", session.endedAt ?: JSONObject.NULL)
            .put("adaptador", session.adapterName)
            .put("distanciaKm", session.distanceKm)
            .put("velocidadMaxKmh", session.maxSpeed)
            .put("rpmMax", session.maxRpm)
            .put("mejor0a60Ms", session.best0to60Ms ?: JSONObject.NULL)
            .put("mejor0a100Ms", session.best0to100Ms ?: JSONObject.NULL)
            .put("combustibleLitros", session.fuelLiters ?: JSONObject.NULL)
            .put("costoCombustibleCop", session.fuelCostCop ?: JSONObject.NULL)
            .put("ecoScore", session.ecoScore ?: JSONObject.NULL)
            .toString()
    }
}
