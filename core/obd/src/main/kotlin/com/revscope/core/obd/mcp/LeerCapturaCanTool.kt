package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.Candump
import com.revscope.core.obd.escucha.CapturasCanRepositorio
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class LeerCapturaCanTool @Inject constructor(
    private val repositorio: CapturasCanRepositorio,
) : McpTool {

    override val name = "leer_captura_can"
    override val description = "Lee una página de tramas de una captura CAN guardada en formato candump"
    override val inputSchema = McpSchemas.objeto(
        "id" to McpSchemas.texto("Identificador de la captura"),
        "desde" to McpSchemas.entero("Índice de la primera trama (por defecto 0)").put("minimum", 0),
        "limite" to McpSchemas.entero("Máximo de tramas a devolver (1-1000, por defecto 500)")
            .put("minimum", 1).put("maximum", MAX_LIMITE),
        requeridos = listOf("id"),
    )

    override suspend fun call(arguments: JSONObject): String {
        val id = arguments.opt("id") as? String
            ?: return error("id debe ser texto")
        if (id.isBlank()) return error("id no puede estar vacío")
        val desde = McpArgumentos.entero(arguments, "desde", 0, 0)
            ?: return error("desde debe ser un entero mayor o igual a 0")
        val limite = McpArgumentos.entero(arguments, "limite", DEFAULT_LIMITE, 1, MAX_LIMITE.toLong())
            ?: return error("limite debe ser un entero entre 1 y 1000")
        val captura = repositorio.leer(id) ?: return error("no existe la captura $id")
        val inicio = desde.coerceAtMost(captura.tramas.size.toLong()).toInt()
        val lineas = captura.tramas.drop(inicio).take(limite.toInt())
            .map { Candump.linea(it, captura.inicioEpochMs) }
        return CapturasCanRepositorio.aJson(captura)
            .put("total", captura.totalTramas)
            .put("desde", desde)
            .put("lineas", JSONArray(lineas))
            .toString()
    }

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()

    private companion object {
        const val DEFAULT_LIMITE = 500L
        const val MAX_LIMITE = 1_000
    }
}
