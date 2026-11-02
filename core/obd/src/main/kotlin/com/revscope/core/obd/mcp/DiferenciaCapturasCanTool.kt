package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.BitCambiado
import com.revscope.core.obd.escucha.ByteCambiado
import com.revscope.core.obd.escucha.CapturasCanRepositorio
import com.revscope.core.obd.escucha.Diferencia
import com.revscope.core.obd.escucha.DiferenciaCan
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class DiferenciaCapturasCanTool @Inject constructor(
    private val repositorio: CapturasCanRepositorio,
) : McpTool {

    override val name = "diferencia_capturas_can"
    override val description =
        "Compara capturas CAN base y de acción para detectar IDs, bytes y bits que cambiaron"
    override val inputSchema = McpSchemas.objeto(
        "base" to McpSchemas.texto("Identificador de la captura base"),
        "accion" to McpSchemas.texto("Identificador de la captura tomada durante la acción"),
        requeridos = listOf("base", "accion"),
    )

    override suspend fun call(arguments: JSONObject): String {
        val baseId = arguments.opt("base") as? String ?: return error("base debe ser texto")
        val accionId = arguments.opt("accion") as? String ?: return error("accion debe ser texto")
        if (baseId.isBlank()) return error("base no puede estar vacío")
        if (accionId.isBlank()) return error("accion no puede estar vacío")
        val base = repositorio.leer(baseId) ?: return error("no existe la captura base $baseId")
        val accion = repositorio.leer(accionId) ?: return error("no existe la captura acción $accionId")
        val diferencia = DiferenciaCan.calcular(base.tramas, accion.tramas)
        return serializar(baseId, accionId, diferencia)
    }

    private fun serializar(
        base: String,
        accion: String,
        diferencia: Diferencia,
    ): String {
        val truncado = diferencia.bytes.size > MAX_CAMBIOS || diferencia.bits.size > MAX_CAMBIOS
        val salida = JSONObject()
            .put("base", base)
            .put("accion", accion)
            .put("idsNuevos", JSONArray(diferencia.idsNuevos))
            .put("idsAusentes", JSONArray(diferencia.idsAusentes))
            .put("bytes", JSONArray(diferencia.bytes.take(MAX_CAMBIOS).map(::byteJson)))
            .put("bits", JSONArray(diferencia.bits.take(MAX_CAMBIOS).map(::bitJson)))
        if (truncado) salida.put("truncado", true)
        return salida.toString()
    }

    private fun byteJson(byte: ByteCambiado): JSONObject = JSONObject()
        .put("id", byte.id)
        .put("byte", byte.byte)
        .put("valorBase", "%02X".format(byte.valorBase))
        .put("valoresNuevos", JSONArray(byte.valoresNuevos.map { "%02X".format(it) }))

    private fun bitJson(bit: BitCambiado): JSONObject = JSONObject()
        .put("id", bit.id)
        .put("byte", bit.byte)
        .put("bit", bit.bit)
        .put("valorBase", bit.valorBase)

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()

    private companion object {
        const val MAX_CAMBIOS = 200
    }
}
