package com.revscope.core.obd.workshop

import org.json.JSONArray
import org.json.JSONObject

// v1 (hasta 1.20.0): array de hallazgos. v2: {"v":2,"items":[…],"metricas":{…}}; los dos se leen.
object HealthReportFormato {

    const val VERSION_ACTUAL = 2

    data class ChequeoGuardado(
        val version: Int,
        val items: List<DiagnosticRules.Diagnosis>,
        val metricas: MetricasChequeo?,
    )

    private val VACIO = ChequeoGuardado(version = 0, items = emptyList(), metricas = null)

    fun escribir(items: List<DiagnosticRules.Diagnosis>, metricas: MetricasChequeo): String = JSONObject()
        .put("v", VERSION_ACTUAL)
        .put("items", itemsJson(items))
        .put("metricas", metricasJson(metricas))
        .toString()

    fun leer(json: String): ChequeoGuardado {
        val texto = json.trim()
        return runCatching {
            if (texto.startsWith("[")) leerV1(JSONArray(texto)) else leerV2(JSONObject(texto))
        }.getOrDefault(VACIO)
    }

    fun itemsJson(items: List<DiagnosticRules.Diagnosis>): JSONArray = JSONArray(items.map(::itemJson))

    fun metricasJson(m: MetricasChequeo): JSONObject = JSONObject()
        .putOpt("stft", m.stft)
        .putOpt("ltft", m.ltft)
        .putOpt("correccionTotal", m.correccionTotal)
        .putOpt("voltaje", m.voltaje)
        .putOpt("motorEncendido", m.motorEncendido)
        .putOpt("ect", m.ect)
        .putOpt("monitoresCompletos", m.monitoresCompletos)
        .putOpt("monitoresTotales", m.monitoresTotales)
        .put("dtcs", JSONArray(m.dtcs))
        .put("dtcsLeidos", m.dtcsLeidos)

    fun metricasDe(json: JSONObject?): MetricasChequeo? {
        if (json == null) return null
        return MetricasChequeo(
            stft = json.doubleONulo("stft"),
            ltft = json.doubleONulo("ltft"),
            voltaje = json.doubleONulo("voltaje"),
            motorEncendido = json.booleanONulo("motorEncendido"),
            ect = json.doubleONulo("ect"),
            monitoresCompletos = json.enteroONulo("monitoresCompletos"),
            monitoresTotales = json.enteroONulo("monitoresTotales"),
            dtcs = json.optJSONArray("dtcs").textos(),
            dtcsLeidos = json.optBoolean("dtcsLeidos", true),
        )
    }

    private fun leerV1(array: JSONArray) = ChequeoGuardado(version = 1, items = items(array), metricas = null)

    private fun leerV2(objeto: JSONObject) = ChequeoGuardado(
        version = objeto.optInt("v", VERSION_ACTUAL),
        items = items(objeto.optJSONArray("items") ?: JSONArray()),
        metricas = metricasDe(objeto.optJSONObject("metricas")),
    )

    private fun items(array: JSONArray): List<DiagnosticRules.Diagnosis> =
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::itemONulo) }

    private fun itemONulo(o: JSONObject): DiagnosticRules.Diagnosis? {
        val nivel = DiagnosticRules.Nivel.entries.firstOrNull { it.name == o.optString("nivel") } ?: return null
        return DiagnosticRules.Diagnosis(
            nivel = nivel,
            area = o.optString("area"),
            titulo = o.optString("titulo"),
            causaProbable = o.optString("causa"),
        )
    }

    private fun itemJson(d: DiagnosticRules.Diagnosis): JSONObject = JSONObject()
        .put("area", d.area)
        .put("nivel", d.nivel.name)
        .put("titulo", d.titulo)
        .put("causa", d.causaProbable)

    private fun JSONObject.doubleONulo(clave: String): Double? =
        if (has(clave) && !isNull(clave)) optDouble(clave).takeUnless { it.isNaN() } else null

    private fun JSONObject.enteroONulo(clave: String): Int? = doubleONulo(clave)?.toInt()

    private fun JSONObject.booleanONulo(clave: String): Boolean? =
        if (has(clave) && !isNull(clave)) optBoolean(clave) else null

    private fun JSONArray?.textos(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}
