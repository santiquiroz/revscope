package com.revscope.core.obd.mcp

import com.revscope.core.obd.catalogo.CatalogoPropietario
import com.revscope.core.obd.catalogo.CodecCatalogo
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class GetCatalogoTool @Inject constructor(
    private val catalogo: CatalogoPropietario,
    private val sessionManager: ObdSessionManager,
) : McpTool {
    override val name = "get_catalogo"
    override val description =
        "Catálogo de operaciones propietarias (DID, escrituras y rutinas) de fuentes públicas y guardadas por el dueño o la IA. " +
            "Revisa verificado y riesgo antes de usar; muchas entradas no están verificadas en este vehículo."
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "marca" to McpSchemas.texto("Filtrar por marca"),
        "modelo" to McpSchemas.texto("Filtrar por modelo"),
        "tipo" to McpSchemas.enumString(listOf("lectura", "escritura", "rutina"), "Tipo de operación"),
        "texto" to McpSchemas.texto("Buscar en id, descripción, módulo y notas"),
        "limite" to McpSchemas.entero("Máximo de entradas (1 a 200; por defecto 50)"),
    )

    override suspend fun call(arguments: JSONObject): String {
        val limite = arguments.optInt("limite", LIMITE_PREDETERMINADO).coerceIn(1, LIMITE_MAXIMO)
        val entradas = catalogo.buscar(
            argumento(arguments, "marca"),
            argumento(arguments, "modelo"),
            argumento(arguments, "tipo"),
            argumento(arguments, "texto"),
        )
        return JSONObject()
            .put("aviso", catalogo.aviso() ?: JSONObject.NULL)
            .put("perfilActivo", sessionManager.activeProfile.value?.name ?: JSONObject.NULL)
            .put("porMarca", JSONObject(catalogo.resumenPorMarca()))
            .put("total", entradas.size)
            .put("entradas", JSONArray(entradas.take(limite).map(CodecCatalogo::aJson)))
            .toString()
    }

    private fun argumento(arguments: JSONObject, nombre: String): String? =
        arguments.optString(nombre).takeIf { arguments.has(nombre) && !arguments.isNull(nombre) && it.isNotBlank() }

    private companion object {
        const val LIMITE_PREDETERMINADO = 50
        const val LIMITE_MAXIMO = 200
    }
}
