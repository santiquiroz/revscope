package com.revscope.core.intelligence.provider

import org.json.JSONArray
import org.json.JSONObject

private const val ANTHROPIC_TEXT_BLOCK_TYPE = "text"
private const val OPENAI_OUTPUT_TEXT_BLOCK_TYPE = "output_text"

/**
 * Pure JSON → text extraction for each provider's wire format. No I/O, no provider
 * instances — kept separate from [AiProvider] implementations so they're trivially
 * unit-testable against synthetic fixtures (see AiResponseParsersTest).
 *
 * Every function returns [Result.failure] on malformed/unexpected JSON instead of throwing.
 */
object AiResponseParsers {

    /**
     * Anthropic Messages API: concatenates every `text` content block, skipping
     * `server_tool_use`/`web_search_tool_result` blocks interleaved by the web_search tool.
     */
    fun parseAnthropicResponse(responseBody: String): Result<String> = runCatching {
        val content = JSONObject(responseBody).getJSONArray("content")
        concatenateBlocks(content) { block ->
            block.optString("type") == ANTHROPIC_TEXT_BLOCK_TYPE
        }
    }

    /** OpenAI Chat Completions API: choices[0].message.content. */
    fun parseOpenAiChatResponse(responseBody: String): Result<String> = runCatching {
        JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    /** OpenAI Responses API (used for web_search): output[].content[] blocks of type output_text. */
    fun parseOpenAiResponsesResponse(responseBody: String): Result<String> = runCatching {
        val output = JSONObject(responseBody).getJSONArray("output")
        buildString {
            for (i in 0 until output.length()) {
                val contentBlocks = output.getJSONObject(i).optJSONArray("content") ?: continue
                append(
                    concatenateBlocks(contentBlocks) { block ->
                        block.optString("type") == OPENAI_OUTPUT_TEXT_BLOCK_TYPE
                    },
                )
            }
        }
    }

    /** Gemini generateContent API: candidates[0].content.parts[].text. */
    fun parseGeminiResponse(responseBody: String): Result<String> = runCatching {
        val parts = JSONObject(responseBody)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
        buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text"))
            }
        }
    }

    /**
     * Human-readable message for a non-2xx response. The big four wrap API errors as
     * `{"error": {"message": ...}}`; some OpenAI-compatible servers (LM Studio) use a
     * plain `{"error": "..."}` string. A missing/non-JSON body degrades to just the code.
     */
    fun httpErrorMessage(providerTag: String, code: Int, errorBody: String?): String {
        val detail = errorBody
            ?.let { extractApiErrorMessage(it) }
            ?.take(MAX_ERROR_DETAIL_CHARS)
        return if (detail == null) "$providerTag HTTP $code" else "$providerTag HTTP $code: $detail"
    }

    private fun extractApiErrorMessage(body: String): String? = runCatching {
        when (val error = JSONObject(body).opt("error")) {
            is JSONObject -> error.optString("message").takeIf { it.isNotBlank() }
            is String -> error.takeIf { it.isNotBlank() }
            else -> null
        }
    }.getOrNull()

    private const val MAX_ERROR_DETAIL_CHARS = 200

    private fun concatenateBlocks(blocks: JSONArray, matches: (JSONObject) -> Boolean): String =
        buildString {
            for (i in 0 until blocks.length()) {
                val block = blocks.getJSONObject(i)
                if (matches(block)) append(block.optString("text"))
            }
        }
}
