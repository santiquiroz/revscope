package com.revscope.core.intelligence.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Generic OpenAI-compatible chat/completions endpoint at a user-editable base URL —
 * covers LM Studio, DeepSeek, Groq, OpenRouter, etc. No server-side web search tool
 * across these backends, so [supportsWebSearch] is always false. Plain [HttpURLConnection]
 * (not Https-only) since local endpoints like LM Studio are typically http://.
 */
class OpenAiCompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String?,
    private val model: String = DEFAULT_MODEL,
) : AiProvider {

    override val providerId: String = "custom"
    override val displayName: String = "Compatible OpenAI"
    override val supportsWebSearch: Boolean = false

    override suspend fun complete(request: AiRequest): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection()
            conn.outputStream.use { it.write(buildBody(request)) }
            val code = conn.responseCode
            return@withContext if (code in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().readText()
                AiResponseParsers.parseOpenAiChatResponse(responseBody)
            } else {
                val errorBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                Result.failure(Exception(AiResponseParsers.httpErrorMessage("Compatible OpenAI", code, errorBody)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openConnection(): HttpURLConnection =
        (URL(chatCompletionsUrl()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            apiKey?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
        }

    private fun buildBody(request: AiRequest): ByteArray = JSONObject().apply {
        put("model", model)
        put(
            "messages",
            JSONArray().apply {
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
            },
        )
        put("max_tokens", request.maxTokens)
    }.toString().toByteArray(Charsets.UTF_8)

    private fun chatCompletionsUrl(): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    companion object {
        const val DEFAULT_MODEL = "local-model"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
