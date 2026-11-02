package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.CapturasCanRepositorio
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ListarCapturasCanTool @Inject constructor(
    private val repositorio: CapturasCanRepositorio,
) : McpTool {

    override val name = "listar_capturas_can"
    override val description = "Lista las capturas CAN guardadas, de la más nueva a la más antigua"
    override val inputSchema = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String =
        JSONObject()
            .put("capturas", JSONArray(repositorio.listar().map { CapturasCanRepositorio.aJsonConOrigen(it) }))
            .toString()
}
