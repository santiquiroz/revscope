package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.CapturasCanRepositorio
import com.revscope.core.obd.escucha.MuestraGps
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class LeerGpsCapturaCanTool @Inject constructor(
    private val repositorio: CapturasCanRepositorio,
) : McpTool {

    override val name = "leer_gps_captura_can"
    override val description =
        "Lee una página de muestras GPS (1 Hz, sin posición) de una captura CAN de ruta hecha desde la app"
    override val inputSchema = McpSchemas.objeto(
        "id" to McpSchemas.texto("Identificador de la captura"),
        "desde" to McpSchemas.entero("Índice de la primera muestra (por defecto 0)").put("minimum", 0),
        "limite" to McpSchemas.entero("Máximo de muestras a devolver (1-1000, por defecto 500)")
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
        if (!repositorio.existe(id)) return error("no existe la captura $id")
        val muestras = repositorio.leerGps(id)
        if (muestras.isEmpty()) return error("la captura $id no tiene muestras GPS")
        val inicio = desde.coerceAtMost(muestras.size.toLong()).toInt()
        return JSONObject()
            .put("id", id)
            .put("total", muestras.size)
            .put("desde", desde)
            .put("muestras", JSONArray(muestras.drop(inicio).take(limite.toInt()).map(::muestraJson)))
            .toString()
    }

    private fun muestraJson(m: MuestraGps): JSONObject =
        JSONObject()
            .put("tMs", m.tMs)
            .put("velocidadKmh", decimal(m.velocidadKmh))
            .put("precisionM", decimal(m.precisionM))
            .put("rumboGrados", decimal(m.rumboGrados))

    // Pasar por el texto evita que 12.3f salga como 12.300000190734863.
    private fun decimal(valor: Float?): Any = valor?.toString()?.toDouble() ?: JSONObject.NULL

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()

    private companion object {
        const val DEFAULT_LIMITE = 500L
        const val MAX_LIMITE = 1_000
    }
}
