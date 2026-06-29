package com.revscope.core.intelligence.dtc

import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
private const val CLAUDE_MODEL = "claude-haiku-4-5-20251001"
private const val MAX_TOKENS = 350
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 20_000

/**
 * Explains DTC fault codes in plain language using the Claude API.
 *
 * The explanation is contextual: current sensor readings are included in the prompt
 * so Claude can reason about likely causes (e.g. "fuel trim +18% + P0171 = likely
 * vacuum leak, not injector").
 *
 * Responses are cached in-memory for the session to avoid repeated API calls for
 * the same code.
 *
 * Falls back gracefully when no API key is configured or the call fails.
 *
 * @param apiKeyProvider Lambda that returns the user's Claude API key or null.
 *                       Called on each [explain] invocation so key changes take effect
 *                       without restarting the ViewModel.
 */
class DtcExplainer(private val apiKeyProvider: () -> String?) {

    private val cache = mutableMapOf<String, DtcExplanation>()

    suspend fun explain(dtcCode: String, context: List<ObdReading>): DtcExplanation {
        cache[dtcCode]?.let { return it.copy(source = "cache") }

        val apiKey = apiKeyProvider()
            ?: return DtcExplanation.noApiKey(dtcCode)

        val result = callClaude(dtcCode, context, apiKey)
        cache[dtcCode] = result
        return result
    }

    private suspend fun callClaude(
        dtcCode: String,
        context: List<ObdReading>,
        apiKey: String,
    ): DtcExplanation = withContext(Dispatchers.IO) {
        try {
            val contextText = context
                .filter { it.unit.isNotEmpty() }
                .joinToString("\n") { "  PID ${it.pid}: ${it.value} ${it.unit}" }

            val userMessage = buildString {
                appendLine("Código de falla OBD-II: $dtcCode")
                if (contextText.isNotBlank()) {
                    appendLine("Lecturas actuales del sensor:")
                    appendLine(contextText)
                }
                appendLine()
                append(
                    "Explica este código en 2-3 oraciones para un conductor no técnico. " +
                    "Incluye: (1) qué significa en lenguaje simple, " +
                    "(2) causas probables según los sensores actuales, " +
                    "(3) urgencia: seguro de conducir / revisar pronto / detener inmediatamente."
                )
            }

            val body = JSONObject().apply {
                put("model", CLAUDE_MODEL)
                put("max_tokens", MAX_TOKENS)
                put("system", "Eres un mecánico experto que explica fallas de motor de forma clara y concisa.")
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    }
                ))
            }.toString().toByteArray(Charsets.UTF_8)

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
            val text = JSONObject(responseText)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")

            DtcExplanation(code = dtcCode, explanation = text.trim(), source = "claude")
        } catch (e: Exception) {
            Timber.e(e, "DtcExplainer: API call failed for $dtcCode")
            DtcExplanation.fallback(dtcCode)
        }
    }
}
