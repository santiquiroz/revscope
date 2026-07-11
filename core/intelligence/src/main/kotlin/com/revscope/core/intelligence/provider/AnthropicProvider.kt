package com.revscope.core.intelligence.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Claude via the Anthropic Messages API. Extracted from the pre-multi-provider
 * DtcExplainer/LocalInfoFetcher — same headers, timeouts and web_search tool shape.
 */
class AnthropicProvider(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : AiProvider {

    override val providerId: String = "anthropic"
    override val displayName: String = "Claude (Anthropic)"
    override val supportsWebSearch: Boolean = true

    override suspend fun complete(request: AiRequest): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection()
            conn.outputStream.use { it.write(buildBody(request)) }
            val code = conn.responseCode
            return@withContext if (code in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().readText()
                AiResponseParsers.parseAnthropicResponse(responseBody)
            } else {
                val errorBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                Result.failure(Exception(AiResponseParsers.httpErrorMessage("Anthropic", code, errorBody)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openConnection(): HttpsURLConnection =
        (URL(API_URL).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
        }

    private fun buildBody(request: AiRequest): ByteArray = JSONObject().apply {
        put("model", model)
        put("max_tokens", request.maxTokens)
        request.system?.let { put("system", it) }
        put(
            "messages",
            JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", request.user)
                },
            ),
        )
        if (request.needsWebSearch) put("tools", webSearchTool())
    }.toString().toByteArray(Charsets.UTF_8)

    private fun webSearchTool(): JSONArray = JSONArray().put(
        JSONObject().apply {
            put("type", "web_search_20250305")
            put("name", "web_search")
            put("max_uses", WEB_SEARCH_MAX_USES)
        },
    )

    companion object {
        const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val WEB_SEARCH_MAX_USES = 2
    }
}
