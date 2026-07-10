package com.revscope.core.obd.mcp

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

private const val JSONRPC_VERSION = "2.0"
private const val PROTOCOL_VERSION = "2025-03-26"
private const val SERVER_NAME = "revscope"

private const val PARSE_ERROR_CODE = -32700
private const val METHOD_NOT_FOUND_CODE = -32601
private const val INVALID_PARAMS_CODE = -32602

private const val METHOD_INITIALIZE = "initialize"
private const val METHOD_INITIALIZED_NOTIFICATION = "notifications/initialized"
private const val METHOD_TOOLS_LIST = "tools/list"
private const val METHOD_TOOLS_CALL = "tools/call"

/** One MCP tool: read-only vehicle query exposed over JSON-RPC's `tools/call`. */
interface McpTool {
    val name: String
    val description: String
    val inputSchema: JSONObject

    /** [arguments] is the `tools/call` params' `arguments` object — returns a compact JSON string. */
    suspend fun call(arguments: JSONObject): String
}

/**
 * Pure JSON-RPC 2.0 dispatcher for RevScope's MCP "streamable HTTP" transport: a single POST
 * per call, a single JSON response, no SSE (see plan6 Task 4). Depends only on org.json and the
 * [McpTool] contract, so it's fully testable with fake tool lambdas — [McpHttpServer] is the
 * NanoHTTPD adapter that feeds it raw request bodies over the wire.
 */
class McpDispatcher(
    tools: List<McpTool>,
    private val serverVersion: String = "1.0.0",
) {

    private val toolsByName = tools.associateBy { it.name }

    /** Returns the JSON-RPC response string, or null for notifications (no response expected). */
    suspend fun dispatch(rawRequest: String): String? {
        val request = parseOrNull(rawRequest)
            ?: return errorResponse(JSONObject.NULL, PARSE_ERROR_CODE, "Parse error").toString()
        val id = request.opt("id") ?: JSONObject.NULL
        val response = when (val method = request.optString("method")) {
            METHOD_INITIALIZE -> successResponse(id, initializeResult())
            METHOD_INITIALIZED_NOTIFICATION -> null
            METHOD_TOOLS_LIST -> successResponse(id, toolsListResult())
            METHOD_TOOLS_CALL -> handleToolsCall(id, request.optJSONObject("params"))
            else -> errorResponse(id, METHOD_NOT_FOUND_CODE, "Method not found: $method")
        }
        return response?.toString()
    }

    private suspend fun handleToolsCall(id: Any, params: JSONObject?): JSONObject {
        val toolName = params?.optString("name")?.takeIf { it.isNotBlank() }
            ?: return errorResponse(id, INVALID_PARAMS_CODE, "Missing tool name")
        val tool = toolsByName[toolName]
            ?: return errorResponse(id, INVALID_PARAMS_CODE, "Unknown tool: $toolName")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        return successResponse(id, runTool(tool, arguments))
    }

    /**
     * Runs the tool and always returns a normal `tools/call` result — an unexpected exception
     * becomes `isError: true` content per MCP convention, not a JSON-RPC protocol error. A
     * protocol error (-32602) is reserved for missing/invalid params before the tool even runs.
     */
    private suspend fun runTool(tool: McpTool, arguments: JSONObject): JSONObject = try {
        toolCallResult(tool.call(arguments))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        toolErrorResult()
    }

    private fun initializeResult(): JSONObject = JSONObject()
        .put("protocolVersion", PROTOCOL_VERSION)
        .put("capabilities", JSONObject().put("tools", JSONObject()))
        .put("serverInfo", JSONObject().put("name", SERVER_NAME).put("version", serverVersion))

    private fun toolsListResult(): JSONObject =
        JSONObject().put("tools", JSONArray(toolsByName.values.map(::toolSummary)))

    private fun toolSummary(tool: McpTool): JSONObject = JSONObject()
        .put("name", tool.name)
        .put("description", tool.description)
        .put("inputSchema", tool.inputSchema)

    private fun toolCallResult(text: String): JSONObject =
        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))

    private fun toolErrorResult(): JSONObject =
        JSONObject()
            .put("isError", true)
            .put(
                "content",
                JSONArray().put(JSONObject().put("type", "text").put("text", "error interno de la herramienta")),
            )

    private fun successResponse(id: Any, result: JSONObject): JSONObject = JSONObject()
        .put("jsonrpc", JSONRPC_VERSION)
        .put("id", id)
        .put("result", result)

    private fun errorResponse(id: Any, code: Int, message: String): JSONObject = JSONObject()
        .put("jsonrpc", JSONRPC_VERSION)
        .put("id", id)
        .put("error", JSONObject().put("code", code).put("message", message))

    private fun parseOrNull(raw: String): JSONObject? = try {
        JSONObject(raw)
    } catch (e: Exception) {
        null
    }
}
