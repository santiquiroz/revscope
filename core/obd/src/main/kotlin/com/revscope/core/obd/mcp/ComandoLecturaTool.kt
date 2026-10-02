package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.diagnostics.ModuleDiscovery
import com.revscope.core.obd.diagnostics.uds.ClaseComando
import com.revscope.core.obd.diagnostics.uds.ComandoHex
import com.revscope.core.obd.diagnostics.uds.EnvioUds
import com.revscope.core.obd.diagnostics.uds.PasoUdsJson
import com.revscope.core.obd.diagnostics.uds.RespuestaUds
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ComandoLecturaTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val registry: PidRegistry,
) : McpTool {

    override val name = "comando_lectura"
    override val description =
        "Envía un comando diagnóstico de solo lectura (servicios 01 02 03 06 07 09 0A 19 1A 21 22 23 24 y AT RV/DPN/I); " +
            "los demás se rechazan, incluido el control de sesión 10 y el tester present 3E (van por comando_escritura)"
    override val inputSchema = McpSchemas.objeto(
        "comando" to McpSchemas.texto("Comando hexadecimal de lectura o AT RV, AT DPN, AT I"),
        "header" to McpSchemas.texto("Header CAN 11-bit opcional, por ejemplo 7E0")
            .put("pattern", "^[0-9A-Fa-f]{3}$"),
        requeridos = listOf("comando"),
    )

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinEnlace()
        val rawComando = arguments.opt("comando") as? String ?: return error("comando debe ser texto")
        val comando = ComandoHex.validar(rawComando).getOrElse { return error(it.message ?: "comando inválido") }
        if (comando.clase != ClaseComando.LECTURA) {
            return error("«${comando.texto}» no es un comando de lectura; las escrituras van por comando_escritura y la programación está bloqueada")
        }
        val header = if (arguments.has("header")) {
            val raw = arguments.opt("header") as? String
                ?: return error("header debe ser un identificador CAN de 11 bits de tres dígitos hexadecimales")
            if (!ModuleDiscovery.isValid11BitHeader(raw)) {
                return error("header debe ser un identificador CAN de 11 bits de tres dígitos hexadecimales")
            }
            raw.trim().uppercase()
        } else null
        if (comando.bytes.isEmpty() && header != null) return error("los comandos AT no admiten header")
        val lectura = sessionManager.withDiagnosticLease(LEASE_OWNER) { bt ->
            if (comando.bytes.isEmpty()) {
                LecturaComando(ResponseParser.cleanResponse(bt.exchange("${comando.texto}\r")), emptyList(), null)
            } else {
                val crudo = EnvioUds.enviar(bt, header, comando.texto)
                val respuestas = EnvioUds.interpretar(crudo, comando, header)
                LecturaComando(crudo, respuestas, decodificar(comando.bytes, respuestas))
            }
        }
        return lectura.fold(
            onSuccess = { resultado ->
                JSONObject().put("conectado", true).put("comando", comando.texto)
                    .put("header", header ?: JSONObject.NULL).put("crudo", resultado.crudo)
                    .put("respuestas", JSONArray(resultado.respuestas.map(PasoUdsJson::respuesta)))
                    .put("decodificado", resultado.decodificado ?: JSONObject.NULL).toString()
            },
            onFailure = { error(it.message ?: "no se pudo enviar el comando", conectado = true) },
        )
    }

    private fun decodificar(bytes: List<Int>, respuestas: List<RespuestaUds>): JSONObject? {
        if (bytes.firstOrNull() != 0x01) return null
        val positiva = respuestas.filterIsInstance<RespuestaUds.Positiva>().firstOrNull() ?: return null
        val pidByte = positiva.datos.firstOrNull() ?: return null
        val pid = "%02X".format(pidByte)
        val lectura = registry.evaluate(pid, positiva.datos.drop(1).map(Int::toByte).toByteArray()) ?: return null
        return JSONObject().put("pid", lectura.pid)
            .put("nombre", registry.getDefinition(pid)?.nameEs ?: JSONObject.NULL)
            .put("valor", lectura.value).put("unidad", lectura.unit)
    }

    private fun error(mensaje: String, conectado: Boolean = false): String =
        JSONObject().put("error", mensaje).apply { if (conectado) put("conectado", true) }.toString()

    private fun sinEnlace(): String =
        JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()

    private data class LecturaComando(
        val crudo: String,
        val respuestas: List<RespuestaUds>,
        val decodificado: JSONObject?,
    )

    private companion object {
        const val LEASE_OWNER = "mcp:comando_lectura"
    }
}
