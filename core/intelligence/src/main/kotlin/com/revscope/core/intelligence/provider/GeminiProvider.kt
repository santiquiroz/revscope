package com.revscope.core.intelligence.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Google Gemini via the generateContent REST API. API key travels as a query param. */
class GeminiProvider(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : AiProvider {

    override val providerId: String = "gemini"
    override val displayName: String = "Gemini (Google)"
    override val supportsWebSearch: Boolean = true

    override suspend fun complete(request: AiRequest): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection()
            conn.outputStream.use { it.write(buildBody(request)) }
            val responseBody = conn.inputStream.bufferedReader().readText()
            AiResponseParsers.parseGeminiResponse(responseBody)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openConnection(): HttpsURLConnection {
        val url = "$BASE_URL/$model:generateContent?key=$apiKey"
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
        }
    }

    private fun buildBody(request: AiRequest): ByteArray = JSONObject().apply {
        request.system?.let { put("system_instruction", textPart(it)) }
        put("contents", JSONArray().put(textPart(request.user)))
        if (request.needsWebSearch) {
            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }
    }.toString().toByteArray(Charsets.UTF_8)

    private fun textPart(text: String): JSONObject = JSONObject().apply {
        put("parts", JSONArray().put(JSONObject().put("text", text)))
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
