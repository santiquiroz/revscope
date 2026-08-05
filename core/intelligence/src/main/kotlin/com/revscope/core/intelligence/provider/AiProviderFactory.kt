package com.revscope.core.intelligence.provider

/** Provider id constants — must match the literal values SecureKeyStore (:core:data) uses. */
const val AI_PROVIDER_ANTHROPIC = "anthropic"
const val AI_PROVIDER_OPENAI = "openai"
const val AI_PROVIDER_GEMINI = "gemini"
const val AI_PROVIDER_CUSTOM = "custom"
const val AI_PROVIDER_NODO = "nodo"

/** Nodo corre en este mismo teléfono, así que su URL es siempre la misma salvo cambio de puerto. */
const val NODO_BASE_URL_POR_DEFECTO = "http://127.0.0.1:8080/v1"

/**
 * Resolved provider selection, read from settings by the caller. Plain data (no
 * DataStore/SecureKeyStore types) so this class — and [AiProviderFactory] — stay
 * usable from :core:intelligence without a compile dependency on :core:data.
 */
data class AiProviderSelection(
    val provider: String,
    val apiKey: String?,
    val model: String?,
    val customBaseUrl: String?,
)

/**
 * Builds the currently selected [AiProvider], or null when it can't be built (no API key,
 * or "custom" with no base URL). [selectionProvider] arrives as a lambda instead of an
 * injected dependency — same pattern as DtcExplainer's `apiKeyProvider`: :core:intelligence
 * has no compile-time visibility into :core:data, where the DataStore/SecureKeyStore-backed
 * selection actually lives (wired in :app's IntelligenceModule).
 */
class AiProviderFactory(private val selectionProvider: suspend () -> AiProviderSelection) {

    suspend fun current(): AiProvider? {
        val selection = selectionProvider()
        val apiKey = selection.apiKey?.takeIf { it.isNotBlank() }
        val model = selection.model?.takeIf { it.isNotBlank() }
        return when (selection.provider) {
            AI_PROVIDER_OPENAI -> apiKey?.let { OpenAiProvider(it, model ?: OpenAiProvider.DEFAULT_MODEL) }
            AI_PROVIDER_GEMINI -> apiKey?.let { GeminiProvider(it, model ?: GeminiProvider.DEFAULT_MODEL) }
            AI_PROVIDER_CUSTOM -> customProvider(selection.customBaseUrl, apiKey, model)
            AI_PROVIDER_NODO -> nodoProvider(selection.customBaseUrl, apiKey, model)
            else -> apiKey?.let { AnthropicProvider(it, model ?: AnthropicProvider.DEFAULT_MODEL) }
        }
    }

    /** A diferencia del genérico, sin URL no falla: cae a la de Nodo en localhost. */
    private fun nodoProvider(baseUrl: String?, apiKey: String?, model: String?): AiProvider =
        OpenAiCompatibleProvider(
            baseUrl?.takeIf { it.isNotBlank() } ?: NODO_BASE_URL_POR_DEFECTO,
            apiKey,
            model ?: OpenAiCompatibleProvider.DEFAULT_MODEL,
        )

    private fun customProvider(baseUrl: String?, apiKey: String?, model: String?): AiProvider? {
        val url = baseUrl?.takeIf { it.isNotBlank() } ?: return null
        return OpenAiCompatibleProvider(url, apiKey, model ?: OpenAiCompatibleProvider.DEFAULT_MODEL)
    }
}
