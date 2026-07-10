package com.revscope.core.intelligence.dtc

import com.revscope.core.intelligence.provider.AiProvider
import com.revscope.core.intelligence.provider.AiRequest
import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

private const val MAX_TOKENS = 350
private const val SYSTEM_PROMPT = "Eres un mecánico experto que explica fallas de motor de forma clara y concisa."

/**
 * Explains DTC fault codes in plain language using the user's configured AI provider
 * (Claude, OpenAI, Gemini or a compatible-OpenAI endpoint — see AiProviderFactory).
 *
 * The explanation is contextual: current sensor readings are included in the prompt
 * so the model can reason about likely causes (e.g. "fuel trim +18% + P0171 = likely
 * vacuum leak, not injector").
 *
 * Responses are cached in-memory for the session to avoid repeated API calls for
 * the same code.
 *
 * Falls back gracefully when no provider is configured or the call fails.
 *
 * @param providerProvider Suspend lambda that returns the active [AiProvider] or null.
 *                          Called on each [explain] invocation so provider/key changes
 *                          take effect without restarting the ViewModel.
 */
class DtcExplainer(private val providerProvider: suspend () -> AiProvider?) {

    private val cache = ConcurrentHashMap<String, DtcExplanation>()

    suspend fun explain(dtcCode: String, context: List<ObdReading>): DtcExplanation {
        cache[dtcCode]?.let { return it.copy(source = "cache") }

        val provider = providerProvider()
            ?: return DtcExplanation.noApiKey(dtcCode)

        val result = callProvider(provider, dtcCode, context)
        cache[dtcCode] = result
        return result
    }

    private suspend fun callProvider(
        provider: AiProvider,
        dtcCode: String,
        context: List<ObdReading>,
    ): DtcExplanation = try {
        val request = AiRequest(
            system = SYSTEM_PROMPT,
            user = buildUserMessage(dtcCode, context),
            maxTokens = MAX_TOKENS,
            needsWebSearch = false,
        )
        provider.complete(request).fold(
            onSuccess = { text -> DtcExplanation(code = dtcCode, explanation = text.trim(), source = provider.providerId) },
            onFailure = { e ->
                Timber.e(e, "DtcExplainer: API call failed for $dtcCode")
                DtcExplanation.fallback(dtcCode)
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "DtcExplainer: API call failed for $dtcCode")
        DtcExplanation.fallback(dtcCode)
    }

    private fun buildUserMessage(dtcCode: String, context: List<ObdReading>): String {
        val contextText = context
            .filter { it.unit.isNotEmpty() }
            .joinToString("\n") { "  PID ${it.pid}: ${it.value} ${it.unit}" }

        return buildString {
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
                    "(3) urgencia: seguro de conducir / revisar pronto / detener inmediatamente.",
            )
        }
    }
}
