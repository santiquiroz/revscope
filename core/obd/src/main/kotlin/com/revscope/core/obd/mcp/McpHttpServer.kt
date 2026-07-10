package com.revscope.core.obd.mcp

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import timber.log.Timber

private const val MCP_PATH = "/mcp"
private const val CONTENT_TYPE_JSON = "application/json"
private const val BEARER_PREFIX = "Bearer "
private const val MAX_BODY_BYTES = 64_000L

/**
 * MCP "streamable HTTP" transport: a single POST /mcp per JSON-RPC call, single JSON response,
 * no SSE (see plan6 Task 4). NanoHTTPD is thread-per-request, so bridging into the suspend
 * [McpDispatcher.dispatch] via [runBlocking] on that request thread is acceptable — every tool
 * only does fast local DAO/StateFlow reads, never network IO.
 */
class McpHttpServer(
    hostname: String,
    port: Int,
    private val dispatcher: McpDispatcher,
    private val token: String,
) : NanoHTTPD(hostname, port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST || session.uri != MCP_PATH) return notFound()
        if (!isAuthorized(session)) return unauthorized()
        if (!hasAcceptableContentLength(session)) return payloadTooLarge()
        val body = readBody(session) ?: return badRequest()
        val responseJson = runBlocking { dispatcher.dispatch(body) } ?: return accepted()
        return jsonResponse(Response.Status.OK, responseJson)
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val header = session.headers["authorization"] ?: return false
        return header == "$BEARER_PREFIX$token"
    }

    /** Rejects bodies without a declared size, or larger than [MAX_BODY_BYTES], before parsing. */
    private fun hasAcceptableContentLength(session: IHTTPSession): Boolean {
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: return false
        return contentLength in 0..MAX_BODY_BYTES
    }

    private fun readBody(session: IHTTPSession): String? = try {
        val files = HashMap<String, String>()
        session.parseBody(files)
        files["postData"]
    } catch (e: Exception) {
        Timber.w(e, "McpHttpServer: failed to read request body")
        null
    }

    private fun notFound() = jsonResponse(Response.Status.NOT_FOUND, """{"error":"not found"}""")
    private fun unauthorized() = jsonResponse(Response.Status.UNAUTHORIZED, """{"error":"unauthorized"}""")
    private fun badRequest() = jsonResponse(Response.Status.BAD_REQUEST, """{"error":"bad request"}""")
    private fun payloadTooLarge() = jsonResponse(Response.Status.PAYLOAD_TOO_LARGE, """{"error":"payload too large"}""")
    private fun accepted() = jsonResponse(Response.Status.NO_CONTENT, "")

    private fun jsonResponse(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, CONTENT_TYPE_JSON, body)
}
