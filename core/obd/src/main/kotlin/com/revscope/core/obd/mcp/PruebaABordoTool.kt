package com.revscope.core.obd.mcp

import com.revscope.core.obd.diagnostics.uds.ComandoHex
import com.revscope.core.obd.diagnostics.uds.PasoUds
import com.revscope.core.obd.diagnostics.uds.PasoUdsJson
import com.revscope.core.obd.diagnostics.uds.ParserUds
import com.revscope.core.obd.diagnostics.uds.RespuestaUds
import com.revscope.core.obd.diagnostics.uds.SecuenciaUds
import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import com.revscope.core.obd.mcp.escritura.EscrituraJson
import com.revscope.core.obd.mcp.escritura.ResultadoEscritura
import com.revscope.core.obd.mcp.escritura.SolicitudEscritura
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class PruebaABordoTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val ejecutor: EjecutorEscritura,
) : McpTool {

    override val name = "prueba_a_bordo"
    override val description =
        "Ejecuta pruebas a bordo del modo 08, por ejemplo una prueba de fugas EVAP; sin tid consulta " +
            "los identificadores compatibles."
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "tid" to McpSchemas.texto("Identificador de prueba de 2 hex"),
        "datos" to McpSchemas.texto("Datos hex opcionales de hasta 5 bytes, solo con tid"),
    )
    override val permiso = McpPermiso.ESCRITURA

    override suspend fun call(arguments: JSONObject): String {
        val tidTexto = arguments.optString("tid").trim().uppercase()
        if (tidTexto.isEmpty()) {
            if (arguments.has("datos")) return error("datos solo se puede usar junto con tid")
            return listar()
        }
        if (!tidTexto.matches(Regex("^[0-9A-F]{2}$"))) return error("tid debe tener 2 dígitos hex")
        val datos = parsearDatos(arguments.optString("datos"))
            ?: return error("datos debe contener hasta 5 bytes hex")
        return ejecutar(tidTexto, datos)
    }

    private suspend fun listar(): String {
        val lectura = sessionManager.withDiagnosticLease("mcp:prueba_a_bordo") { bt ->
            bt.exchange("08 00\r", 3_000)
        }
        val respuesta = lectura.getOrNull()?.let { ParserUds.interpretar(it, 0x08, conHeaders = false).firstOrNull() }
        if (respuesta !is RespuestaUds.Positiva || respuesta.datos.size < 5 || respuesta.datos[0] != 0) {
            return JSONObject()
                .put("soportado", false)
                .put("tids", JSONArray())
                .put("mensaje", "la ECU no soporta el modo 08 (habitual: casi ningún vehículo lo implementa)")
                .toString()
        }
        val tids = tidsSoportados(respuesta.datos.drop(1))
        return JSONObject().put("soportado", true).put("tids", JSONArray(tids)).toString()
    }

    private fun tidsSoportados(bytes: List<Int>): List<String> =
        (0 until 32).mapNotNull { indice ->
            val byte = bytes[indice / 8]
            val bit = 7 - indice % 8
            if (byte and (1 shl bit) != 0) (indice + 1).toString(16).uppercase().padStart(2, '0') else null
        }

    private suspend fun ejecutar(tid: String, datos: List<Int>): String {
        val texto = (listOf("08", tid) + datos.map { "%02X".format(it) }).joinToString(" ")
        val comando = ComandoHex.validar(texto).getOrThrow()
        val solicitud = SolicitudEscritura(
            tool = name,
            resumen = "Ejecutar prueba a bordo $tid (destino por defecto, UDS $texto)",
            header = null,
            pasos = listOf(comando.texto),
        )
        var pasos = emptyList<PasoUds>()
        val resultado = ejecutor.ejecutar(solicitud) { bt ->
            pasos = SecuenciaUds.correr(bt, null, listOf(comando))
            pasos.map { it.crudo ?: "(sin respuesta: ${it.error})" }
        }
        return EscrituraJson.de(resultado)
            .put("header", JSONObject.NULL)
            .put("pasos", PasoUdsJson.pasos(pasos))
            .put("mensaje", mensaje(resultado, pasos.firstOrNull(), tid))
            .toString()
    }

    private fun mensaje(resultado: ResultadoEscritura, paso: PasoUds?, tid: String): String =
        when (resultado) {
            is ResultadoEscritura.NoAutorizada -> resultado.motivo
            is ResultadoEscritura.Fallida -> resultado.error
            is ResultadoEscritura.Ejecutada -> when (val respuesta = paso?.respuestas?.firstOrNull()) {
                is RespuestaUds.Positiva -> "la ECU aceptó la prueba a bordo $tid"
                is RespuestaUds.Negativa -> "la ECU rechazó la prueba a bordo: ${respuesta.nombreNrc}"
                is RespuestaUds.SinRespuesta, null -> "la ECU no respondió a la prueba a bordo $tid"
            }
        }

    private fun parsearDatos(entrada: String): List<Int>? {
        val compacto = entrada.filterNot(Char::isWhitespace).uppercase()
        if (compacto.isEmpty()) return emptyList()
        if (compacto.length > 10 || compacto.length % 2 != 0 || !compacto.matches(Regex("^[0-9A-F]+$"))) {
            return null
        }
        return compacto.chunked(2).map { it.toInt(16) }
    }

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()
}
