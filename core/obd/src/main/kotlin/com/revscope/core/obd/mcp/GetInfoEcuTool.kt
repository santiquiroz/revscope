package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.diagnostics.uds.ParserUds
import com.revscope.core.obd.diagnostics.uds.RespuestaUds
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class GetInfoEcuTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "get_info_ecu"
    override val description =
        "Identificación de la ECU: VIN, calibración de software, CVN, nombre, protocolo y voltaje del adaptador"
    override val inputSchema = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinEnlace()
        val errores = JSONArray()
        val info = sessionManager.withDiagnosticLease(LEASE_OWNER) { bt ->
            val vin = leerDato(bt, "vin", "09 02", 0x02, errores, ::ascii)
            val cal = leerDato(bt, "calibraciones", "09 04", 0x04, errores, ::calibraciones)
            val cvn = leerDato(bt, "cvn", "09 06", 0x06, errores, ::cvn)
            val ecu = leerDato(bt, "nombreEcu", "09 0A", 0x0A, errores, ::ascii)
            val protocolo = leerAt(bt, "protocolo", "AT DPN", errores)
            val voltaje = leerAt(bt, "voltajeAdaptador", "AT RV", errores)
            InfoEcu(vin, cal, cvn, ecu, protocolo, voltaje)
        }
        val datos = info.getOrNull()
        if (datos == null) errores.put(JSONObject().put("elemento", "conexión").put("mensaje", info.exceptionOrNull()?.message ?: "no se pudo leer"))
        return JSONObject()
            .put("conectado", true)
            .put("vin", datos?.vin ?: JSONObject.NULL)
            .put("calibraciones", datos?.calibraciones ?: JSONObject.NULL)
            .put("cvn", datos?.cvn ?: JSONObject.NULL)
            .put("nombreEcu", datos?.nombreEcu ?: JSONObject.NULL)
            .put("protocolo", datos?.protocolo ?: JSONObject.NULL)
            .put("voltajeAdaptador", datos?.voltaje ?: JSONObject.NULL)
            .put("errores", errores)
            .toString()
    }

    private suspend fun <T> leerDato(
        bt: com.revscope.core.obd.connection.Transport,
        nombre: String,
        comando: String,
        tipo: Int,
        errores: JSONArray,
        decodificar: (List<Int>) -> T,
    ): T? = try {
        val respuestas = ParserUds.interpretar(bt.exchange("$comando\r"), 0x09, conHeaders = false)
        val positiva = respuestas.filterIsInstance<RespuestaUds.Positiva>().firstOrNull {
            it.datos.firstOrNull() == tipo
        }
        if (positiva == null) {
            errores.put(JSONObject().put("elemento", nombre).put("mensaje", motivo(respuestas)))
            null
        } else {
            decodificar(positiva.datos.drop(2))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errores.put(JSONObject().put("elemento", nombre).put("mensaje", e.message ?: "sin respuesta"))
        null
    }

    private suspend fun leerAt(
        bt: com.revscope.core.obd.connection.Transport,
        nombre: String,
        comando: String,
        errores: JSONArray,
    ): String? = try {
        val resultado = ResponseParser.cleanResponse(bt.exchange("$comando\r"))
        if (resultado.isBlank() || ResponseParser.isErrorResponse(resultado)) {
            errores.put(JSONObject().put("elemento", nombre).put("mensaje", resultado.ifBlank { "sin respuesta" }))
            null
        } else {
            resultado
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errores.put(JSONObject().put("elemento", nombre).put("mensaje", e.message ?: "sin respuesta"))
        null
    }

    private fun motivo(respuestas: List<RespuestaUds>): String = when (val r = respuestas.firstOrNull()) {
        is RespuestaUds.Negativa -> "NRC ${r.nombreNrc}"
        is RespuestaUds.SinRespuesta -> r.motivo
        else -> "respuesta sin datos"
    }

    private fun ascii(bytes: List<Int>): String =
        bytes.mapNotNull { it.toChar().takeIf { char -> char.isPrintableAscii() && char.code != 0 } }
            .joinToString("").trim()

    private fun calibraciones(bytes: List<Int>): JSONArray =
        JSONArray(bytes.chunked(16).map { ascii(it) }.filter(String::isNotBlank))

    private fun cvn(bytes: List<Int>): JSONArray =
        JSONArray(bytes.chunked(4).filter { it.size == 4 }.map { chunk -> chunk.joinToString("") { "%02X".format(it) } })

    private fun Char.isPrintableAscii(): Boolean = code in 0x20..0x7E

    private fun sinEnlace(): String =
        JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()

    private data class InfoEcu(
        val vin: String?,
        val calibraciones: JSONArray?,
        val cvn: JSONArray?,
        val nombreEcu: String?,
        val protocolo: String?,
        val voltaje: String?,
    )

    private companion object {
        const val LEASE_OWNER = "mcp:get_info_ecu"
    }
}
