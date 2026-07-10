package com.revscope.core.obd.mcp

import com.revscope.core.data.db.dao.MaintenanceDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.trip.MaintenanceCalculator
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Próximos ítems de mantenimiento por kilometraje del perfil activo. */
class GetMantenimientoTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val maintenanceDao: MaintenanceDao,
    private val sessionDao: SessionDao,
) : McpTool {

    override val name = "get_mantenimiento"
    override val description = "Ítems de mantenimiento configurados y kilómetros restantes para cada uno"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        val profile = sessionManager.activeProfile.value
            ?: return JSONObject().put("error", "sin perfil de vehículo activo").toString()
        val items = maintenanceDao.listForProfile(profile.id)
        if (items.isEmpty()) return JSONObject().put("items", JSONArray()).toString()
        val sumaSesiones = sessionDao.observeSumDistanceKmForProfile(profile.id).first()
        val odometro = MaintenanceCalculator.odometroActual(profile.odometerBaseKm, sumaSesiones)
        val estados = MaintenanceCalculator.calculate(odometro, items)
        return JSONObject()
            .put("odometroActualKm", odometro)
            .put(
                "items",
                JSONArray(
                    estados.map {
                        JSONObject()
                            .put("nombre", it.item.nombre)
                            .put("kmRestantes", it.kmRestantes)
                            .put("nivel", it.nivel.name)
                    },
                ),
            )
            .toString()
    }
}
