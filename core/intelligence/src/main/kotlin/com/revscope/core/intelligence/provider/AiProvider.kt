package com.revscope.core.intelligence.provider

/**
 * A single AI completion request, shared by every [AiProvider] implementation.
 *
 * @property system Optional system/instructions prompt (some providers fold this into
 *                   the message list instead of a dedicated field).
 * @property user User-turn prompt text.
 * @property maxTokens Upper bound on generated output tokens.
 * @property needsWebSearch When true, the provider should enable its server-side web
 *                           search tool if it has one — see [supportsWebSearch].
 */
data class AiRequest(
    val system: String? = null,
    val user: String,
    val maxTokens: Int,
    val needsWebSearch: Boolean = false,
)

/**
 * A backend that can turn an [AiRequest] into generated text. Implementations own their
 * own HTTP/JSON wire format; callers only see [complete].
 */
interface AiProvider {

    /** Stable identifier persisted in settings — "anthropic" | "openai" | "gemini" | "custom". */
    val providerId: String

    /** Human-readable name for the "Probar conexión" button and diagnostics. */
    val displayName: String

    /** Whether [complete] can honor [AiRequest.needsWebSearch]. */
    val supportsWebSearch: Boolean

    /** Runs the request. Never throws — network/parse failures come back as [Result.failure]. */
    suspend fun complete(request: AiRequest): Result<String>
}
