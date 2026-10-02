package com.revscope.core.obd.mcp

import com.revscope.core.obd.diagnostics.ModuleDiscovery
import com.revscope.core.obd.diagnostics.uds.ClaseComando
import com.revscope.core.obd.diagnostics.uds.ComandoHex
import com.revscope.core.obd.diagnostics.uds.ComandoValidado
import com.revscope.core.obd.diagnostics.uds.PasoUds
import com.revscope.core.obd.diagnostics.uds.PasoUdsJson
import com.revscope.core.obd.diagnostics.uds.SecuenciaUds
import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import com.revscope.core.obd.mcp.escritura.EscrituraJson
import com.revscope.core.obd.mcp.escritura.SolicitudEscritura
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ComandoEscrituraTool @Inject constructor(
    private val ejecutor: EjecutorEscritura,
) : McpTool {

    override val name = "comando_escritura"
    override val description =
        "Ejecuta una secuencia libre de comandos diagnósticos para una ECU o módulo (control de sesión, " +
            "security access 27, escritura 2E, IO control 2F, rutinas 31, reset 11, borrado 14 y procedimientos " +
            "del fabricante). Bloquea flasheo (10 02, 34-38, 3D). Cada llamada pide confirmación en el teléfono " +
            "salvo que el bypass esté activo; las guardas físicas siempre aplican. Úsalo para procedimientos " +
            "específicos del fabricante que el catálogo no cubra."
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "pasos" to McpSchemas.arrayDeStrings("Entre 1 y 10 comandos hex")
            .put("minItems", 1)
            .put("maxItems", 10),
        "descripcion" to McpSchemas.texto("Qué pretende hacer la IA; se muestra al dueño"),
        "header" to McpSchemas.texto("Header CAN opcional de 3 hex"),
        requeridos = listOf("pasos", "descripcion"),
    )
    override val permiso = McpPermiso.ESCRITURA

    override suspend fun call(arguments: JSONObject): String {
        val entradas = argumentosPasos(arguments.optJSONArray("pasos"))
            ?: return error("pasos debe contener entre 1 y 10 comandos hex")
        val descripcion = arguments.optString("descripcion").trim()
        if (descripcion.isEmpty()) return error("descripcion no puede estar vacía")
        val header = arguments.optString("header").trim().uppercase().takeIf { it.isNotEmpty() }
        if (header != null && (!header.matches(Regex("^[0-9A-F]{3}$")) ||
                !ModuleDiscovery.isValid11BitHeader(header))
        ) {
            return error("header debe ser un CAN ID de 11 bits de 3 hex (por ejemplo 7E0)")
        }
        val comandos = validarPasos(entradas).getOrElse {
            return error(it.message ?: "comando inválido")
        }
        return ejecutar(header, descripcion, comandos)
    }

    private fun validarPasos(entradas: List<String>): Result<List<ComandoValidado>> {
        val comandos = mutableListOf<ComandoValidado>()
        for ((indice, entrada) in entradas.withIndex()) {
            if (entrada.trim().startsWith("AT", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("paso ${indice + 1}: los comandos AT no van por aquí"))
            }
            val comando = ComandoHex.validar(entrada).getOrElse {
                return Result.failure(IllegalArgumentException("paso ${indice + 1}: ${it.message ?: "comando inválido"}"))
            }
            if (comando.clase == ClaseComando.BLOQUEADO) {
                return Result.failure(
                    IllegalArgumentException(
                        "paso ${indice + 1} «${comando.texto}» bloqueado: programación/flasheo no permitido por Bluetooth",
                    ),
                )
            }
            comandos += comando
        }
        return Result.success(comandos)
    }

    private suspend fun ejecutar(
        header: String?,
        descripcion: String,
        comandos: List<ComandoValidado>,
    ): String {
        val textos = comandos.map { it.texto }
        val destino = header ?: "destino por defecto"
        val solicitud = SolicitudEscritura(
            tool = name,
            resumen = "$descripcion — $destino: ${textos.joinToString(" → ")}",
            header = header,
            pasos = textos,
            requiereMotorApagado = comandos.any { it.bytes.firstOrNull() == 0x11 },
        )
        var pasos = emptyList<PasoUds>()
        val resultado = ejecutor.ejecutar(solicitud) { bt ->
            pasos = SecuenciaUds.correr(bt, header, comandos)
            pasos.map { it.crudo ?: "(sin respuesta: ${it.error})" }
        }
        val pasoFallido = pasos.indexOfFirst { !it.exitoso }.takeIf { it >= 0 }?.plus(1) ?: JSONObject.NULL
        return EscrituraJson.de(resultado)
            .put("header", header ?: JSONObject.NULL)
            .put("pasos", PasoUdsJson.pasos(pasos))
            .put("completada", pasos.size == comandos.size && pasos.all(PasoUds::exitoso))
            .put("pasoFallido", pasoFallido)
            .toString()
    }

    private fun argumentosPasos(array: JSONArray?): List<String>? {
        if (array == null || array.length() !in 1..10) return null
        return (0 until array.length()).map { index ->
            (array.opt(index) as? String)?.trim() ?: return null
        }
    }

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()
}
