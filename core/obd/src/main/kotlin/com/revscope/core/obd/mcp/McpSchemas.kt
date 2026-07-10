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
}
