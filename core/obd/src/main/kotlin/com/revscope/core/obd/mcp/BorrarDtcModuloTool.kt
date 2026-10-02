package com.revscope.core.obd.mcp

import com.revscope.core.obd.diagnostics.ModuleDiscovery
import com.revscope.core.obd.diagnostics.uds.ComandoHex
import com.revscope.core.obd.diagnostics.uds.ComandoValidado
import com.revscope.core.obd.diagnostics.uds.PasoUds
import com.revscope.core.obd.diagnostics.uds.PasoUdsJson
import com.revscope.core.obd.diagnostics.uds.RespuestaUds
import com.revscope.core.obd.diagnostics.uds.SecuenciaUds
import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import com.revscope.core.obd.mcp.escritura.EscrituraJson
import com.revscope.core.obd.mcp.escritura.ResultadoEscritura
import com.revscope.core.obd.mcp.escritura.SolicitudEscritura
import org.json.JSONObject
import javax.inject.Inject

class BorrarDtcModuloTool @Inject constructor(
    private val ejecutor: EjecutorEscritura,
) : McpTool {

    override val name = "borrar_dtc_modulo"
    override val description =
        "Borra los DTC de un módulo específico por su header CAN (UDS 14), por ejemplo ABS o carrocería " +
            "que el modo 04 no alcanza; requiere confirmación del dueño en el teléfono."
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "header" to McpSchemas.texto("Header CAN de 3 hex del módulo, por ejemplo 7E0"),
        "grupo" to McpSchemas.texto("Grupo de DTC de 6 hex; por defecto FFFFFF borra todos")
            .put("default", "FFFFFF"),
        requeridos = listOf("header"),
    )
    override val permiso = McpPermiso.ESCRITURA

    override suspend fun call(arguments: JSONObject): String {
        val header = arguments.optString("header").trim().uppercase()
        if (!headerValido(header)) return error("header debe ser un CAN ID de 11 bits de 3 hex (por ejemplo 7E0)")
        val grupo = arguments.optString("grupo", "FFFFFF").trim().uppercase()
        if (!grupo.matches(Regex("^[0-9A-F]{6}$"))) return error("grupo debe tener 6 dígitos hex")
        val comando = ComandoHex.validar("14 ${grupo.chunked(2).joinToString(" ")}").getOrElse {
            return error(it.message ?: "comando UDS inválido")
        }
        return ejecutar(header, comando)
    }

    private suspend fun ejecutar(
        header: String,
        comando: ComandoValidado,
    ): String {
        val texto = comando.texto
        val solicitud = SolicitudEscritura(
            tool = name,
            resumen = "Borrar los DTC del módulo $header (UDS $texto)",
            header = header,
            pasos = listOf(texto),
        )
        var pasos = emptyList<PasoUds>()
        val resultado = ejecutor.ejecutar(solicitud) { bt ->
            pasos = SecuenciaUds.correr(bt, header, listOf(comando))
            pasos.map { it.crudo ?: "(sin respuesta: ${it.error})" }
        }
        return EscrituraJson.de(resultado)
            .put("header", header)
            .put("pasos", PasoUdsJson.pasos(pasos))
            .put("mensaje", mensaje(resultado, pasos.firstOrNull(), header))
            .toString()
    }

    private fun mensaje(resultado: ResultadoEscritura, paso: PasoUds?, header: String): String =
        when (resultado) {
            is ResultadoEscritura.NoAutorizada -> resultado.motivo
            is ResultadoEscritura.Fallida -> resultado.error
            is ResultadoEscritura.Ejecutada -> when (val respuesta = paso?.respuestas?.firstOrNull()) {
                is RespuestaUds.Positiva -> "códigos del módulo $header borrados"
                is RespuestaUds.Negativa -> "el módulo rechazó el borrado: ${respuesta.nombreNrc}"
                is RespuestaUds.SinRespuesta, null ->
                    "el módulo $header no respondió (¿existe? prueba descubrir_modulos)"
            }
    }

    private fun headerValido(header: String): Boolean =
        header.matches(Regex("^[0-9A-F]{3}$")) && ModuleDiscovery.isValid11BitHeader(header)

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()
}
