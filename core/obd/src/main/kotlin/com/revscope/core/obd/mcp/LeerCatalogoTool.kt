package com.revscope.core.obd.mcp

import com.revscope.core.obd.catalogo.CatalogoPropietario
import com.revscope.core.obd.catalogo.EntradaCatalogo
import com.revscope.core.obd.catalogo.FormulaCatalogo
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.diagnostics.uds.ClaseComando
import com.revscope.core.obd.diagnostics.uds.ComandoHex
import com.revscope.core.obd.diagnostics.uds.PasoUdsJson
import com.revscope.core.obd.diagnostics.uds.ParserUds
import com.revscope.core.obd.diagnostics.uds.PasoUds
import com.revscope.core.obd.diagnostics.uds.RespuestaUds
import com.revscope.core.obd.diagnostics.uds.SecuenciaUds
import com.revscope.core.obd.diagnostics.uds.ComandoValidado
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONObject
import javax.inject.Inject

class LeerCatalogoTool @Inject constructor(
    private val catalogo: CatalogoPropietario,
    private val sessionManager: ObdSessionManager,
) : McpTool {
    override val name = "leer_catalogo"
    override val description = "Lee una operación propietaria de tipo lectura desde el módulo del vehículo."
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "id" to McpSchemas.texto("Identificador de la entrada del catálogo"),
        requeridos = listOf("id"),
    )

    override suspend fun call(arguments: JSONObject): String {
        val id = arguments.optString("id")
        val entrada = catalogo.porId(id) ?: return error("no existe la entrada $id")
        if (!entrada.esLectura) return error("la entrada $id es de tipo ${entrada.tipo}: ejecútala con ejecutar_catalogo")
        if (sessionManager.connectionState.value !is ConnectionState.Connected) {
            return JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()
        }
        val comandos = validarComandos(entrada.pasos) ?: return error("la entrada $id contiene pasos que no son de lectura")
        return sessionManager.withDiagnosticLease(LEASE_OWNER) { bt ->
            SecuenciaUds.correr(bt, entrada.header, comandos)
        }.fold(
            onSuccess = { pasos -> respuesta(entrada, comandos, pasos) },
            onFailure = { e -> error(e.message ?: "no se pudo leer la entrada $id") },
        )
    }

    private fun validarComandos(pasos: List<String>) =
        pasos.map { paso ->
            ComandoHex.validar(paso).getOrNull()?.takeIf { it.clase == ClaseComando.LECTURA }
        }.takeIf { comandos -> comandos.isNotEmpty() && comandos.all { it != null } }?.filterNotNull()

    private fun respuesta(
        entrada: EntradaCatalogo,
        comandos: List<ComandoValidado>,
        pasos: List<PasoUds>,
    ): String {
        val positiva = pasos.lastOrNull()?.respuestas?.filterIsInstance<RespuestaUds.Positiva>()?.firstOrNull()
        val datos = positiva?.let { SecuenciaUds.datosSinEco(comandos.last().bytes.first(), it) }
        val json = JSONObject()
            .put("id", entrada.id)
            .put("descripcion", entrada.descripcion)
            .put("valor", if (entrada.formula != null && datos != null) FormulaCatalogo.evaluar(entrada.formula, datos) ?: JSONObject.NULL else JSONObject.NULL)
            .put("unidad", entrada.unidad ?: JSONObject.NULL)
            .put("datosHex", datos?.let(ParserUds::hex) ?: JSONObject.NULL)
            .put("pasos", PasoUdsJson.pasos(pasos))
            .put("verificado", entrada.verificado)
            .put("fuente", entrada.fuente ?: JSONObject.NULL)
            .put("notas", entrada.notas ?: JSONObject.NULL)
        if (positiva == null) json.put("mensaje", SIN_RESPUESTA)
        return json.toString()
    }

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()

    private companion object {
        const val LEASE_OWNER = "mcp:leer_catalogo"
        const val SIN_RESPUESTA = "el módulo no respondió positivamente; puede que esta entrada no aplique a este vehículo"
    }
}
