package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.escucha.CapturaCan
import com.revscope.core.obd.escucha.CapturasCanRepositorio
import com.revscope.core.obd.escucha.OpcionesEscucha
import com.revscope.core.obd.escucha.ResumenCaptura
import com.revscope.core.obd.escucha.ResumenId
import com.revscope.core.obd.escucha.ServicioEscuchaCan
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.round

class EscucharCanTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val servicio: ServicioEscuchaCan,
) : McpTool {

    override val name = "escuchar_can"
    override val description =
        "Escucha el bus CAN durante unos segundos y guarda una captura etiquetada en formato candump"
    override val inputSchema = McpSchemas.objeto(
        "etiqueta" to McpSchemas.texto("Etiqueta requerida para identificar la captura")
            .put("minLength", 1).put("maxLength", MAX_ETIQUETA),
        "segundos" to McpSchemas.entero("Duración de la escucha en segundos (1-60, por defecto 10)")
            .put("minimum", 1).put("maximum", 60),
        "filtro" to McpSchemas.texto("Filtro AT CRA opcional: identificador CAN de 3 u 8 dígitos hexadecimales")
            .put("pattern", FILTER_PATTERN),
        requeridos = listOf("etiqueta"),
    )

    override suspend fun call(arguments: JSONObject): String {
        val etiqueta = arguments.opt("etiqueta") as? String
            ?: return error("etiqueta debe ser texto")
        if (etiqueta.isBlank() || etiqueta.length !in 1..MAX_ETIQUETA) {
            return error("etiqueta debe tener entre 1 y 64 caracteres")
        }
        val segundos = McpArgumentos.entero(arguments, "segundos", DEFAULT_SECONDS, 1, MAX_SECONDS)
            ?: return error("segundos debe ser un entero entre 1 y 60")
        val filtro = leerFiltro(arguments) ?: if (arguments.has("filtro")) {
            return error("filtro debe tener 3 u 8 dígitos hexadecimales")
        } else null
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinConexion()

        return servicio.escuchar(OpcionesEscucha(etiqueta, segundos * 1_000L, filtro?.uppercase()))
            .fold(::resultado, { error(it.message ?: "no se pudo escuchar el bus CAN") })
    }

    private fun leerFiltro(arguments: JSONObject): String? {
        if (!arguments.has("filtro")) return null
        val filtro = arguments.opt("filtro") as? String ?: return null
        return filtro.takeIf { FILTER_REGEX.matches(it) }
    }

    private fun resultado(captura: CapturaCan): String {
        val resumen = ResumenCaptura.calcular(captura)
        val salida = CapturasCanRepositorio.aJson(captura)
            .put("resumen", JSONArray(resumen.take(MAX_RESUMEN).map(::resumenJson)))
        if (resumen.size > MAX_RESUMEN) salida.put("resumenTruncado", true)
        if (!captura.silencioso) {
            salida.put("aviso", "el adaptador no aceptó AT CSM1: pudo responder ACK en el bus")
        }
        return salida.toString()
    }

    private fun resumenJson(resumen: ResumenId): JSONObject =
        JSONObject()
            .put("id", resumen.id)
            .put("tramas", resumen.tramas)
            .put("hz", round(resumen.hz * 100) / 100)
            .put("largos", JSONArray(resumen.largos.sorted()))
            .put("bytesQueCambian", JSONArray(resumen.bytesQueCambian))

    private fun sinConexion(): String =
        JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()

    private companion object {
        const val MAX_ETIQUETA = 64
        const val DEFAULT_SECONDS = 10L
        const val MAX_SECONDS = 60L
        const val MAX_RESUMEN = 100
        const val FILTER_PATTERN = "^[0-9A-Fa-fXx]{3}$|^[0-9A-Fa-fXx]{8}$"
        val FILTER_REGEX = Regex(FILTER_PATTERN)
    }
}
