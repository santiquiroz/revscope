package com.revscope.core.obd.mcp

import com.revscope.core.data.db.dao.HealthReportDao
import com.revscope.core.obd.workshop.HealthReportFormato
import org.json.JSONObject
import javax.inject.Inject

/** Último informe de chequeo de salud guardado (mezcla, O2, eléctrico, refrigeración, readiness) con sus métricas. */
class GetChequeoSaludTool @Inject constructor(
    private val healthReportDao: HealthReportDao,
) : McpTool {

    override val name = "get_chequeo_salud"
    override val description =
        "Último chequeo de salud del vehículo — hallazgos por área con su nivel (OK/ATENCION/FALLA) y, desde " +
            "el formato 2, las métricas numéricas (ajustes stft/ltft y corrección total en %, voltaje, " +
            "temperatura del motor, monitores y códigos)"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        val report = healthReportDao.latest()
            ?: return JSONObject().put("error", "sin chequeos registrados").toString()
        val guardado = HealthReportFormato.leer(report.resultsJson)
        return JSONObject()
            .put("id", report.id)
            .put("fecha", report.timestamp)
            .put("formato", guardado.version)
            .put("items", HealthReportFormato.itemsJson(guardado.items))
            .put("metricas", guardado.metricas?.let(HealthReportFormato::metricasJson) ?: JSONObject.NULL)
            .toString()
    }
}
