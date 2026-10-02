package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.diagnostics.ModuleDiscovery
import com.revscope.core.obd.diagnostics.uds.ComandoHex
import com.revscope.core.obd.diagnostics.uds.EnvioUds
import com.revscope.core.obd.diagnostics.uds.PasoUdsJson
import com.revscope.core.obd.diagnostics.uds.RespuestaUds
import com.revscope.core.obd.diagnostics.uds.SecuenciaUds
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class LeerDidTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "leer_did"
    override val description =
        "Lee identificadores de datos del fabricante (UDS 22) en la ECU de motor o un módulo por header; explora datos propietarios como el modo de manejo TVS"
    override val inputSchema = McpSchemas.objeto(
        "dids" to org.json.JSONObject()
            .put("type", "array")
            .put("minItems", 1)
            .put("maxItems", 32)
            .put("items", org.json.JSONObject().put("type", "string").put("pattern", "^[0-9A-Fa-f]{4}$"))
            .put("description", "DID de cuatro dígitos hexadecimales"),
        "header" to McpSchemas.texto("Header CAN 11-bit opcional, por ejemplo 7E0")
            .put("pattern", "^[0-9A-Fa-f]{3}$"),
        requeridos = listOf("dids"),
    )

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinEnlace()
        val dids = validarDids(arguments.optJSONArray("dids"))
            ?: return error("dids debe contener entre 1 y 32 valores de cuatro dígitos hexadecimales")
        val header = if (arguments.has("header")) {
            val raw = arguments.opt("header") as? String
                ?: return error("header debe ser un identificador CAN de 11 bits de tres dígitos hexadecimales")
            if (!ModuleDiscovery.isValid11BitHeader(raw)) {
                return error("header debe ser un identificador CAN de 11 bits de tres dígitos hexadecimales")
            }
            raw.trim().uppercase()
        } else null
        val lectura = sessionManager.withDiagnosticLease(LEASE_OWNER) { bt ->
            JSONArray(dids.map { did -> leerUno(bt = bt, did = did, header = header) })
        }
        return lectura.fold(
            onSuccess = { JSONObject().put("conectado", true).put("header", header ?: JSONObject.NULL).put("resultados", it).toString() },
            onFailure = { error(it.message ?: "no se pudieron leer los DID", conectado = true) },
        )
    }

    private fun validarDids(array: JSONArray?): List<String>? {
        if (array == null || array.length() !in 1..32) return null
        val dids = (0 until array.length()).map { index -> array.opt(index) as? String ?: return null }
        if (dids.any { !DID.matches(it) }) return null
        return dids.map { it.uppercase() }
    }

    private suspend fun leerUno(
        bt: com.revscope.core.obd.connection.Transport,
        did: String,
        header: String?,
    ): JSONObject {
        val comando = ComandoHex.validar("22 $did").getOrThrow()
        return try {
            val raw = EnvioUds.enviar(bt, header, comando.texto)
            val respuestas = EnvioUds.interpretar(raw, comando, header)
            val resultado = JSONObject().put("did", did).put("respuestas", JSONArray(respuestas.map(PasoUdsJson::respuesta)))
            val positiva = respuestas.filterIsInstance<RespuestaUds.Positiva>().firstOrNull()
            resultado.put("ascii", positiva?.let { ascii(SecuenciaUds.datosSinEco(0x22, it)) } ?: JSONObject.NULL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            JSONObject().put("did", did).put("respuestas", JSONArray())
                .put("error", e.message ?: "sin respuesta del adaptador").put("ascii", JSONObject.NULL)
        }
    }

    private fun ascii(bytes: List<Int>): String =
        bytes.mapNotNull { it.toChar().takeIf { ch -> ch.code in 0x20..0x7E } }.joinToString("").trim()

    private fun error(mensaje: String, conectado: Boolean = false): String =
        JSONObject().put("error", mensaje).apply { if (conectado) put("conectado", true) }.toString()

    private fun sinEnlace(): String =
        JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()

    private companion object {
        const val LEASE_OWNER = "mcp:leer_did"
        val DID = Regex("^[0-9A-Fa-f]{4}$")
    }
}
