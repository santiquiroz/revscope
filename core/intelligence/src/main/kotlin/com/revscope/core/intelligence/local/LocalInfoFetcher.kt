package com.revscope.core.intelligence.local

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.intelligence.provider.AiProvider
import com.revscope.core.intelligence.provider.AiRequest
import com.revscope.core.intelligence.provider.withResponseLanguage
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_TOKENS = 200
private const val NO_INFO_MARKER = "NADA"

/**
 * Asks the user's configured AI provider — with its web_search tool, when it has one —
 * whether there's anything relevant today in a municipality the user just entered:
 * festivals, road closures, alerts. Same [AiProvider] abstraction as [com.revscope.core.intelligence.dtc.DtcExplainer].
 *
 * No in-memory cache: each announcement queries once, and the daily per-municipio
 * cooldown lives in CityInfoAlerter/LocalInfoAlertPolicy (:core:obd), not here.
 *
 * @param providerProvider Same shape as DtcExplainer's — a lambda instead of injecting
 *                          AiProviderFactory directly, since :core:intelligence doesn't
 *                          compile-depend on :core:data (see CityInfoAlerter's doc).
 * @param resolver Resolves the prompt's response-language line.
 */
class LocalInfoFetcher(
    private val resolver: StringResolver,
    private val providerProvider: suspend () -> AiProvider?,
) {

    /** Returns the one-sentence local-info phrase, or null on nothing-relevant/error/no provider/no web search. */
    suspend fun fetchLocalInfo(municipio: String, departamento: String?): String? {
        val provider = providerProvider() ?: return null
        if (!provider.supportsWebSearch) return null
        return callProvider(provider, municipio, departamento)
    }

    private suspend fun callProvider(
        provider: AiProvider,
        municipio: String,
        departamento: String?,
    ): String? = try {
        val request = AiRequest(
            user = buildPrompt(municipio, departamento).withResponseLanguage(resolver),
            maxTokens = MAX_TOKENS,
            needsWebSearch = true,
        )
        provider.complete(request).fold(
            onSuccess = { text -> parseLocalInfo(text) },
            onFailure = { e ->
                Timber.w(e, "LocalInfoFetcher: API call failed for $municipio")
                null
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "LocalInfoFetcher: API call failed for $municipio")
        null
    }

    private fun buildPrompt(municipio: String, departamento: String?): String {
        val lugar = if (departamento.isNullOrBlank()) municipio else "$municipio, $departamento"
        // Cuerpo del prompt: no es texto de interfaz, se queda en español.
        return "Hoy es ${today()}. Acabo de llegar a $lugar, Colombia conduciendo. " + // i18n-ignore
            "En UNA sola frase corta (máx 20 palabras) dime si hay hoy algún evento, festividad, " + // i18n-ignore
            "cierre vial o alerta relevante en este municipio. Si no encuentras nada relevante y " + // i18n-ignore
            "actual, responde exactamente: $NO_INFO_MARKER" // i18n-ignore
    }

    private fun today(): String =
        LocalDate.now(ZoneId.of("America/Bogota"))
            .format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es", "CO"))) // i18n-ignore: fecha del cuerpo del prompt

    private fun parseLocalInfo(rawText: String): String? {
        val text = rawText.trim()
        if (text.isEmpty()) return null

        // Treat as no-info if text equals "NADA" or starts with "NADA" (ignoring case, punctuation, whitespace)
        val normalized = text.uppercase().replace(Regex("[.,!?;:]"), "").trim()
        if (normalized == NO_INFO_MARKER || normalized.startsWith("$NO_INFO_MARKER ")) return null

        return text
    }
}
