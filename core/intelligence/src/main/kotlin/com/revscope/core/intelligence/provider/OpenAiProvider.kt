package com.revscope.core.intelligence.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * OpenAI. Chat Completions for normal requests; the Responses API (with the
 * `web_search` server tool) when [AiRequest.needsWebSearch] is set — Chat Completions
 * has no server-side web search tool, so this is the simplest split that covers both.
 */
class OpenAiProvider(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : AiProvider {

    override val providerId: String = "openai"
    override val displayName: String = "OpenAI"
    override val supportsWebSearch: Boolean = true

    override suspend fun complete(request: AiRequest): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (request.needsWebSearch) completeWithWebSearch(request) else completeChat(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun completeChat(request: AiRequest): Result<String> {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", buildMessages(request))
            put("max_completion_tokens", request.maxTokens)
        }.toString().toByteArray(Charsets.UTF_8)
        return AiResponseParsers.parseOpenAiChatResponse(post(CHAT_URL, body))
    }

    private fun completeWithWebSearch(request: AiRequest): Result<String> {
        val body = JSONObject().apply {
            put("model", model)
            put("input", buildMessages(request))
            put("max_output_tokens", request.maxTokens)
            put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
        }.toString().toByteArray(Charsets.UTF_8)
        return AiResponseParsers.parseOpenAiResponsesResponse(post(RESPONSES_URL, body))
    }

    private fun buildMessages(request: AiRequest): JSONArray = JSONArray().apply {
        request.system?.let {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", it)
                },
            )
        }
        put(
            JSONObject().apply {
                put("role", "user")
                put("content", request.user)
            },
        )
    }

    private fun post(url: String, body: ByteArray): String {
        val conn = (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
        }
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val errorBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
            throw Exception(AiResponseParsers.httpErrorMessage("OpenAI", code, errorBody))
        }
        return conn.inputStream.bufferedReader().readText()
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-5-mini"
        private const val CHAT_URL = "https://api.openai.com/v1/chat/completions"
        private const val RESPONSES_URL = "https://api.openai.com/v1/responses"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
