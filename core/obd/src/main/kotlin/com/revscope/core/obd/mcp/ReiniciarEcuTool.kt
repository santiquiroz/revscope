package com.revscope.core.obd.mcp

import com.revscope.core.obd.diagnostics.ModuleDiscovery
import com.revscope.core.obd.diagnostics.uds.ComandoHex
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

class ReiniciarEcuTool @Inject constructor(
    private val ejecutor: EjecutorEscritura,
) : McpTool {

    override val name = "reiniciar_ecu"
    override val description =
        "Reinicia el ECU del motor u otro módulo (UDS 11); el motor debe estar apagado y el contacto " +
            "encendido, y requiere confirmación del dueño."
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "header" to McpSchemas.texto("Header CAN de 3 hex; por defecto 7E0").put("default", DEFAULT_HEADER),
        "tipo" to McpSchemas.enumString(TIPOS, "Tipo de reinicio; por defecto suave").put("default", TIPO_SUAVE),
    )
    override val permiso = McpPermiso.ESCRITURA

    override suspend fun call(arguments: JSONObject): String {
        val header = arguments.optString("header", DEFAULT_HEADER).trim().uppercase()
        if (!header.matches(Regex("^[0-9A-F]{3}$")) || !ModuleDiscovery.isValid11BitHeader(header)) {
            return error("header debe ser un CAN ID de 11 bits de 3 hex (por ejemplo 7E0)")
        }
        val tipo = arguments.optString("tipo", TIPO_SUAVE)
        val subfuncion = SUBFUNCIONES[tipo] ?: return error("tipo debe ser suave, apagado_encendido o duro")
        val texto = "11 $subfuncion"
        return ejecutar(header, tipo, texto)
    }

    private suspend fun ejecutar(header: String, tipo: String, texto: String): String {
        val comando = ComandoHex.validar(texto).getOrThrow()
        val solicitud = SolicitudEscritura(
            tool = name,
            resumen = "Reiniciar la ECU $header (UDS $texto, reinicio $tipo)",
            header = header,
            pasos = listOf(comando.texto),
            requiereMotorApagado = true,
        )
        var pasos = emptyList<PasoUds>()
        val resultado = ejecutor.ejecutar(solicitud) { bt ->
            pasos = SecuenciaUds.correr(bt, header, listOf(comando))
            pasos.map { it.crudo ?: "(sin respuesta: ${it.error})" }
        }
        return EscrituraJson.de(resultado)
            .put("header", header)
            .put("pasos", PasoUdsJson.pasos(pasos))
            .put("mensaje", mensaje(resultado, pasos.firstOrNull(), header, tipo))
            .toString()
    }

    private fun mensaje(
        resultado: ResultadoEscritura,
        paso: PasoUds?,
        header: String,
        tipo: String,
    ): String = when (resultado) {
        is ResultadoEscritura.NoAutorizada -> resultado.motivo
        is ResultadoEscritura.Fallida -> resultado.error
        is ResultadoEscritura.Ejecutada -> when (val respuesta = paso?.respuestas?.firstOrNull()) {
            is RespuestaUds.Positiva -> "la ECU $header aceptó el reinicio ($tipo)"
            is RespuestaUds.Negativa -> "la ECU rechazó el reinicio: ${respuesta.nombreNrc}"
            is RespuestaUds.SinRespuesta, null ->
                "sin respuesta: normal si la ECU se reinició; verifica con get_estado en unos segundos"
        }
    }

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()

    private companion object {
        const val DEFAULT_HEADER = "7E0"
        const val TIPO_SUAVE = "suave"
        val TIPOS = listOf("suave", "apagado_encendido", "duro")
        val SUBFUNCIONES = mapOf("suave" to "03", "apagado_encendido" to "02", "duro" to "01")
    }
}
