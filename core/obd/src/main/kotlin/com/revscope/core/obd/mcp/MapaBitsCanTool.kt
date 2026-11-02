package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.AnalisisBitsCan
import com.revscope.core.obd.escucha.AnalisisByte
import com.revscope.core.obd.escucha.CapturasCanRepositorio
import com.revscope.core.obd.escucha.IdCan
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class MapaBitsCanTool @Inject constructor(
    private val repositorio: CapturasCanRepositorio,
) : McpTool {

    override val name = "mapa_bits_can"
    override val description = "Analiza la clase y los bits variables de cada byte en una captura CAN"
    override val inputSchema = McpSchemas.objeto(
        "id" to McpSchemas.texto("Identificador de la captura"),
        "can_id" to McpSchemas.texto("Filtra por ID CAN de 3 u 8 dígitos hexadecimales")
            .put("pattern", CAN_ID_PATTERN),
        requeridos = listOf("id"),
    )

    override suspend fun call(arguments: JSONObject): String {
        val id = arguments.opt("id") as? String ?: return error("id debe ser texto")
        if (id.isBlank()) return error("id no puede estar vacío")
        val filtro = leerCanId(arguments) ?: if (arguments.has("can_id")) {
            return error("can_id debe tener 3 u 8 dígitos hexadecimales")
        } else null
        val captura = repositorio.leer(id) ?: return error("no existe la captura $id")
        val tramas = captura.tramas.filter { filtro == null || IdCan.texto(it.id, it.extendido) == filtro }
        val bytes = AnalisisBitsCan.analizar(tramas)
        val salida = JSONObject()
            .put("captura", id)
            .put("bytes", JSONArray(bytes.take(MAX_BYTES).map(::byteJson)))
        if (bytes.size > MAX_BYTES) salida.put("truncado", true)
        return salida.toString()
    }

    private fun leerCanId(arguments: JSONObject): String? {
        if (!arguments.has("can_id")) return null
        val canId = arguments.opt("can_id") as? String ?: return null
        return canId.takeIf { CAN_ID_REGEX.matches(it) }?.uppercase()
    }

    private fun byteJson(byte: AnalisisByte): JSONObject = JSONObject()
        .put("id", byte.id)
        .put("byte", byte.byte)
        .put("clase", byte.clase.name.lowercase())
        .put("distintos", byte.distintos)
        .put("minimo", byte.minimo)
        .put("maximo", byte.maximo)
        .put("bitsQueCambian", JSONArray(byte.bitsQueCambian))
        .put("detalle", byte.detalle ?: JSONObject.NULL)

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()

    private companion object {
        const val MAX_BYTES = 400
        const val CAN_ID_PATTERN = "^[0-9A-Fa-f]{3}$|^[0-9A-Fa-f]{8}$"
        val CAN_ID_REGEX = Regex(CAN_ID_PATTERN)
    }
}
