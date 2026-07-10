package com.revscope.core.intelligence.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
private const val CLAUDE_MODEL = "claude-haiku-4-5-20251001"
private const val MAX_TOKENS = 200
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 20_000
private const val WEB_SEARCH_MAX_USES = 2
private const val NO_INFO_MARKER = "NADA"
private const val TEXT_BLOCK_TYPE = "text"

/**
 * Asks Claude — with the `web_search` server tool — whether there's anything relevant
 * today in a municipality the user just entered: festivals, road closures, alerts.
 * Same HTTP/API-key infrastructure as [com.revscope.core.intelligence.dtc.DtcExplainer].
 *
 * No in-memory cache: each announcement queries once, and the daily per-municipio
 * cooldown lives in CityInfoAlerter/LocalInfoAlertPolicy (:core:obd), not here.
 *
 * @param apiKeyProvider Same shape as DtcExplainer's — a lambda instead of injecting
 *                        SecureKeyStore directly, since :core:intelligence doesn't
 *                        compile-depend on :core:data (see CityInfoAlerter's doc).
 */
class LocalInfoFetcher(private val apiKeyProvider: suspend () -> String?) {

    /** Returns the one-sentence local-info phrase, or null on "nothing relevant"/error/no key. */
    suspend fun fetchLocalInfo(municipio: String, departamento: String?): String? {
        val apiKey = apiKeyProvider() ?: return null
        return callClaude(municipio, departamento, apiKey)
    }

    private suspend fun callClaude(
        municipio: String,
        departamento: String?,
        apiKey: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val body = buildRequestBody(municipio, departamento).toString().toByteArray(Charsets.UTF_8)

            val conn = (URL(CLAUDE_API_URL).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }

            conn.outputStream.use { it.write(body) }

            val responseText = conn.inputStream.bufferedReader().readText()
            val content = JSONObject(responseText).getJSONArray("content")
            parseLocalInfo(concatenateTextBlocks(content))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "LocalInfoFetcher: API call failed for $municipio")
            null
        }
    }

    private fun buildRequestBody(municipio: String, departamento: String?): JSONObject {
        val lugar = if (departamento.isNullOrBlank()) municipio else "$municipio, $departamento"
        val userMessage = "Hoy es ${today()}. Acabo de llegar a $lugar, Colombia conduciendo. " +
            "En UNA sola frase corta (máx 20 palabras) dime si hay hoy algún evento, festividad, " +
            "cierre vial o alerta relevante en este municipio. Si no encuentras nada relevante y " +
            "actual, responde exactamente: $NO_INFO_MARKER"

        return JSONObject().apply {
            put("model", CLAUDE_MODEL)
            put("max_tokens", MAX_TOKENS)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    }
                ),
            )
            put(
                "tools",
                JSONArray().put(
                    JSONObject().apply {
                        put("type", "web_search_20250305")
                        put("name", "web_search")
                        put("max_uses", WEB_SEARCH_MAX_USES)
                    }
                ),
            )
        }
    }

    private fun today(): String =
        LocalDate.now(ZoneId.of("America/Bogota"))
            .format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es", "CO")))

    /** Web-search tool calls interleave text blocks with server_tool_use/tool_result blocks. */
    private fun concatenateTextBlocks(content: JSONArray): String = buildString {
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == TEXT_BLOCK_TYPE) append(block.optString("text"))
        }
    }

    private fun parseLocalInfo(rawText: String): String? {
        val text = rawText.trim()
        return if (text.isEmpty() || text.equals(NO_INFO_MARKER, ignoreCase = true)) null else text
    }
}
