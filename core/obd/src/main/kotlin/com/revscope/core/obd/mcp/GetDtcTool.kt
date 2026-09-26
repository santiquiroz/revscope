package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.diagnostics.DtcLectura
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.DtcServicio
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.RegistroTaller
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * DTC leídos en vivo bajo la concesión de diagnóstico: funciona durante o después de un viaje
 * sin cortar la conexión (el sondeo se pausa solo mientras dura la lectura).
 *
 * Caché de [CACHE_WINDOW_MS] por combinación de argumentos y como mucho una lectura al ECU
 * cada [MIN_INTERVAL_MS]: un cliente MCP insistente no acapara el canal serie. Cada lectura real (no la
 * de caché) queda en la sesión abierta del Taller con origen MCP.
 */
class GetDtcTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val registry: PidRegistry,
    private val registro: RegistroTaller,
) : McpTool {

    internal constructor(
        sessionManager: ObdSessionManager,
        registry: PidRegistry,
        registro: RegistroTaller,
        nowMs: () -> Long,
    ) : this(sessionManager, registry, registro) {
        this.nowMs = nowMs
    }

    override val name = "get_dtc"
    override val description =
        "Códigos de falla (DTC) leídos en vivo: activos (03), pendientes (07), permanentes (0A), testigo MIL " +
            "y freeze frame con el DTC que lo guardó. Funciona durante o después de un viaje sin cortar la " +
            "conexión — requiere adaptador conectado"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "modos" to McpSchemas.arrayDeEnum(MODOS.keys.toList(), "Qué listas leer (por defecto las tres)"),
        "freeze_frame" to McpSchemas.booleano("Leer el freeze frame (por defecto true)"),
        "incluir_crudo" to McpSchemas.booleano("Incluir la respuesta cruda del ECU por comando (por defecto false)"),
    )

    private var nowMs: () -> Long = { System.currentTimeMillis() }
    private val mutex = Mutex()
    private val cache = HashMap<String, Pair<Long, String>>()
    private var ultimaLecturaMs: Long? = null

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) {
            return JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()
        }
        val opciones = opcionesDe(arguments)
        val incluirCrudo = arguments.optBoolean("incluir_crudo", false)
        val clave = "${opciones.servicios.sorted()}|${opciones.freezeFrame}|$incluirCrudo"
        return mutex.withLock {
            cacheVigente(clave) ?: esperaPendiente() ?: leerYCachear(clave, opciones, incluirCrudo)
        }
    }

    private fun opcionesDe(arguments: JSONObject): DtcLectura {
        val modos = arguments.optJSONArray("modos")?.let(::serviciosDe)?.takeIf { it.isNotEmpty() }
        return DtcLectura(
            servicios = modos ?: DtcServicio.entries.toSet(),
            freezeFrame = arguments.optBoolean("freeze_frame", true),
        )
    }

    private fun serviciosDe(array: JSONArray): Set<DtcServicio> =
        (0 until array.length()).mapNotNull { MODOS[array.optString(it)] }.toSet()

    private fun cacheVigente(clave: String): String? {
        val (enMs, json) = cache[clave] ?: return null
        if (nowMs() - enMs >= CACHE_WINDOW_MS) return null
        return JSONObject(json).put("cache", true).toString()
    }

    private fun esperaPendiente(): String? {
        val ultima = ultimaLecturaMs ?: return null
        val restanteMs = MIN_INTERVAL_MS - (nowMs() - ultima)
        if (restanteMs <= 0) return null
        return JSONObject()
            .put("conectado", true)
            .put("error", "lectura de DTC reciente — reintenta en ${(restanteMs + 999) / 1_000} s")
            .toString()
    }

    private suspend fun leerYCachear(clave: String, opciones: DtcLectura, incluirCrudo: Boolean): String {
        ultimaLecturaMs = nowMs()
        val lectura = sessionManager.leerDtcCompleto(LEASE_OWNER, opciones)
        val result = lectura.fold(
            onSuccess = { scan ->
                DtcScanJson.scan(scan, incluirCrudo) { registry.getDefinition(it)?.nameEs }
                    .put("conectado", true)
                    .put("viaje", DtcScanJson.estadoViaje(sessionManager.estadoViaje.value))
                    .toString()
            },
            onFailure = { e ->
                JSONObject().put("conectado", true).put("error", e.message ?: "no se pudo leer DTC").toString()
            },
        )
        lectura.onSuccess { scan ->
            cache[clave] = nowMs() to result
            registro.anotarLecturaDtc(scan, OrigenEvento.MCP)
        }
        return result
    }

    private companion object {
        const val LEASE_OWNER = "mcp:get_dtc"
        const val CACHE_WINDOW_MS = 10_000L
        const val MIN_INTERVAL_MS = 5_000L
        val MODOS = mapOf(
            "activos" to DtcServicio.ACTIVOS,
            "pendientes" to DtcServicio.PENDIENTES,
            "permanentes" to DtcServicio.PERMANENTES,
        )
    }
}
