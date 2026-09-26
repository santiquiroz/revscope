package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.telemetry.SamplingPreset
import com.revscope.core.obd.telemetry.captura.ConfigCaptura
import com.revscope.core.obd.telemetry.captura.EstadoCaptura
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Preset de muestreo, captura activa, tasa medida y adaptador (versión ELM, protocolo). */
class GetMuestreoTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "get_muestreo"
    override val description =
        "Frecuencia de muestreo OBD: preset del sondeo normal, si hay captura rápida activa, tasa medida por PID " +
            "(Hz), peticiones/s, latencia p50/p95, qué la limita y datos del adaptador"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        val captura = sessionManager.captura
        val activa = captura.estado.value as? EstadoCaptura.Activa
        return JSONObject()
            .put("preset", sessionManager.muestreoPreset.value.claveMcp)
            .put("presetsDisponibles", JSONArray(clavesPreset()))
            .put("capturaActiva", activa != null)
            .put("capturaId", activa?.inicio?.id ?: JSONObject.NULL)
            .put("limiteHz", activa?.limiteHz ?: JSONObject.NULL)
            .put("medicion", CapturaJson.estadisticas(captura.estadisticas.value))
            .put("adaptador", CapturaJson.adaptador(sessionManager.infoAdaptador()))
            .toString()
    }
}

/** Cambia el preset del sondeo normal (no la captura rápida). */
class SetMuestreoTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "set_muestreo"
    override val description =
        "Cambia la frecuencia del sondeo normal de todos los PIDs: estandar_2s (por defecto), 1s, 500ms, 250ms o " +
            "maximo. Más rápido gasta más batería. Para uno o pocos sensores a alta tasa usa iniciar_captura"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "preset" to McpSchemas.enumString(clavesPreset(), "Preset de muestreo"),
        requeridos = listOf("preset"),
    )
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        val preset = SamplingPreset.desdeClaveMcp(arguments.optString("preset"))
            ?: return JSONObject().put("error", "preset inválido; usa ${clavesPreset()}").toString()
        sessionManager.cambiarPresetMuestreo(preset)
        return JSONObject().put("preset", preset.claveMcp).toString()
    }
}

/** Arranca la captura rápida: 1 a 6 PIDs a la máxima tasa del adaptador. */
class IniciarCapturaTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "iniciar_captura"
    override val description =
        "Captura rápida tipo escáner: sondea solo 1 a 6 PIDs de modo 01 (ej. [\"49\",\"4A\",\"11\"] = pedal D, " +
            "pedal E y mariposa) lo más rápido que permita el adaptador; los demás gauges quedan en pausa. " +
            "Lee las muestras con get_captura (paginando con desde_seq) y termina con detener_captura"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "pids" to McpSchemas.arrayDeStrings("PIDs de modo 01 en hex, de 1 a 6"),
        "duracion_s" to McpSchemas.entero("Duración máxima en segundos (1-600, por defecto 60)"),
        requeridos = listOf("pids"),
    )
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinEnlaceJson()
        val pids = McpSchemas.strings(arguments.optJSONArray("pids"))
        val duracionS = arguments.optLong("duracion_s", DURACION_DEFAULT_S).coerceIn(1, DURACION_MAX_S)
        return sessionManager.captura.iniciar(ConfigCaptura(pids, duracionS * 1_000))
            .fold(
                onSuccess = { CapturaJson.inicio(it) },
                onFailure = { JSONObject().put("error", it.message ?: "no se pudo iniciar la captura") },
            ).toString()
    }

    companion object {
        const val DURACION_DEFAULT_S = 60L
        const val DURACION_MAX_S = 600L
    }
}

/** Página de muestras de la captura: todas las capturadas entre una llamada y la siguiente. */
class GetCapturaTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "get_captura"
    override val description =
        "Muestras de la captura rápida desde el cursor desde_seq (0 al inicio): series por PID con t_ms relativo " +
            "al inicio y valor. Llama de nuevo con seqSiguiente; perdidas > 0 si el cursor quedó fuera del búfer"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "capturaId" to McpSchemas.texto("Id devuelto por iniciar_captura"),
        "desde_seq" to McpSchemas.entero("Cursor: 0 al inicio, luego seqSiguiente"),
        "max" to McpSchemas.entero("Muestras por página (hasta 2000)"),
        "pids" to McpSchemas.arrayDeStrings("Filtrar a estos PIDs (opcional)"),
        requeridos = listOf("capturaId"),
    )

    override suspend fun call(arguments: JSONObject): String {
        val captura = sessionManager.captura
        val id = arguments.optString("capturaId")
        val max = arguments.optInt("max", MAX_PAGINA).coerceIn(1, MAX_PAGINA)
        val filtro = McpSchemas.strings(arguments.optJSONArray("pids")).takeIf { it.isNotEmpty() }?.toSet()
        val pagina = captura.pagina(id, arguments.optLong("desde_seq", 0).coerceAtLeast(0), max, filtro)
            ?: return JSONObject().put("error", "captura $id no está en memoria").toString()
        val activa = (captura.estado.value as? EstadoCaptura.Activa)?.inicio?.id == id
        return JSONObject()
            .put("capturaId", id)
            .put("activa", activa)
            .put("inicioEpochMs", captura.inicioEpochMs(id) ?: JSONObject.NULL)
            .put("seqSiguiente", pagina.seqSiguiente)
            .put("perdidas", pagina.perdidas)
            .put("n", pagina.muestras.size)
            .put("medicion", if (activa) CapturaJson.estadisticas(captura.estadisticas.value) else JSONObject())
            .put("series", CapturaJson.series(pagina.muestras))
            .toString()
    }

    companion object {
        const val MAX_PAGINA = 2_000
    }
}

/** Detiene la captura, revierte el afinado del ELM y vuelve al muestreo normal. */
class DetenerCapturaTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "detener_captura"
    override val description =
        "Detiene la captura rápida y vuelve al muestreo normal. Devuelve n, Hz, mín, máx y media por PID, " +
            "latencia p50/p95, el motivo de fin y la ruta del CSV en el teléfono"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "capturaId" to McpSchemas.texto("Id devuelto por iniciar_captura"),
    )
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        val captura = sessionManager.captura
        val resumen = captura.detener("detenida desde el MCP") ?: captura.ultimoResumen.value
            ?: return JSONObject().put("error", "no hay captura activa ni terminada").toString()
        return CapturaJson.resumen(resumen).put("activa", false).toString()
    }
}

private fun clavesPreset(): List<String> = SamplingPreset.entries.map { it.claveMcp }
