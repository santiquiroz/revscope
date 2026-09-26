package com.revscope.core.obd.mcp

import org.json.JSONArray
import org.json.JSONObject

/** Small builders for MCP `inputSchema` JSON — plain JSON Schema objects, kept minimal. */
object McpSchemas {

    fun noArguments(): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject())

    fun optionalInt(propertyName: String, description: String): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject().put(propertyName, JSONObject().put("type", "integer").put("description", description)),
        )

    fun requiredInt(propertyName: String, description: String): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject().put(propertyName, JSONObject().put("type", "integer").put("description", description)),
        )
        .put("required", JSONArray().put(propertyName))

    fun objeto(vararg propiedades: Pair<String, JSONObject>, requeridos: List<String> = emptyList()): JSONObject {
        val schema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().apply { propiedades.forEach { (nombre, prop) -> put(nombre, prop) } })
        if (requeridos.isNotEmpty()) schema.put("required", JSONArray(requeridos))
        return schema
    }

    fun booleano(descripcion: String): JSONObject =
        JSONObject().put("type", "boolean").put("description", descripcion)

    fun enumString(valores: List<String>, descripcion: String): JSONObject =
        JSONObject().put("type", "string").put("enum", JSONArray(valores)).put("description", descripcion)

    fun arrayDeEnum(valores: List<String>, descripcion: String): JSONObject =
        JSONObject()
            .put("type", "array")
            .put("items", JSONObject().put("type", "string").put("enum", JSONArray(valores)))
            .put("description", descripcion)

    fun arrayDeStrings(descripcion: String): JSONObject =
        JSONObject()
            .put("type", "array")
            .put("items", JSONObject().put("type", "string"))
            .put("description", descripcion)

    fun entero(descripcion: String): JSONObject = JSONObject().put("type", "integer").put("description", descripcion)

    fun texto(descripcion: String): JSONObject = JSONObject().put("type", "string").put("description", descripcion)

    /** Strings no vacíos de un argumento array (null o ausente = lista vacía). */
    fun strings(array: JSONArray?): List<String> =
        (0 until (array?.length() ?: 0)).mapNotNull { i -> array?.optString(i)?.trim()?.takeIf { it.isNotEmpty() } }
}
