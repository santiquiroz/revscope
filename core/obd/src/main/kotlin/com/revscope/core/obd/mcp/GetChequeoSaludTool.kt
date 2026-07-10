package com.revscope.core.obd.mcp

import com.revscope.core.data.db.dao.HealthReportDao
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Último informe de chequeo de salud guardado (mezcla, O2, eléctrico, refrigeración, readiness). */
class GetChequeoSaludTool @Inject constructor(
    private val healthReportDao: HealthReportDao,
) : McpTool {

    override val name = "get_chequeo_salud"
    override val description = "Último chequeo de salud del vehículo — hallazgos por área con su nivel (OK/ATENCION/FALLA)"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        val report = healthReportDao.latest()
            ?: return JSONObject().put("error", "sin chequeos registrados").toString()
        val items = try {
            JSONArray(report.resultsJson)
        } catch (e: Exception) {
            JSONArray()
        }
        return JSONObject()
            .put("fecha", report.timestamp)
            .put("items", items)
            .toString()
    }
}
