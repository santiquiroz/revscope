package com.revscope.core.intelligence.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseParsersTest {

    // ── Anthropic ────────────────────────────────────────────────────────────

    @Test
    fun `anthropic response with single text block extracts text`() {
        val body = """
            {"content":[{"type":"text","text":"Vacuum leak likely."}]}
        """.trimIndent()

        val result = AiResponseParsers.parseAnthropicResponse(body)

        assertEquals("Vacuum leak likely.", result.getOrNull())
    }

    @Test
    fun `anthropic response skips tool blocks and concatenates only text blocks`() {
        val body = """
            {"content":[
                {"type":"server_tool_use","id":"srvtool_1","name":"web_search"},
                {"type":"web_search_tool_result","tool_use_id":"srvtool_1","content":[]},
                {"type":"text","text":"Hoy hay un festival."},
                {"type":"text","text":" Cierre vial en la vía principal."}
            ]}
        """.trimIndent()

        val result = AiResponseParsers.parseAnthropicResponse(body)

        assertEquals("Hoy hay un festival. Cierre vial en la vía principal.", result.getOrNull())
    }

    @Test
    fun `anthropic response missing content array fails`() {
        val body = """{"id":"msg_1","type":"message"}"""

        val result = AiResponseParsers.parseAnthropicResponse(body)

        assertTrue(result.isFailure)
    }

    @Test
    fun `anthropic response with malformed json fails`() {
        val result = AiResponseParsers.parseAnthropicResponse("not json at all")

        assertTrue(result.isFailure)
    }

    // ── OpenAI Chat Completions ──────────────────────────────────────────────

    @Test
    fun `openai chat response extracts message content`() {
        val body = """
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Fuel trim looks normal."}}]}
        """.trimIndent()

        val result = AiResponseParsers.parseOpenAiChatResponse(body)

        assertEquals("Fuel trim looks normal.", result.getOrNull())
    }

    @Test
    fun `openai chat response missing choices fails`() {
        val body = """{"id":"chatcmpl_1"}"""

        val result = AiResponseParsers.parseOpenAiChatResponse(body)

        assertTrue(result.isFailure)
    }

    @Test
    fun `openai chat response missing message content fails`() {
        val body = """{"choices":[{"index":0,"message":{"role":"assistant"}}]}"""

        val result = AiResponseParsers.parseOpenAiChatResponse(body)

        assertTrue(result.isFailure)
    }

    // ── OpenAI Responses API (web_search) ────────────────────────────────────

    @Test
    fun `openai responses api extracts output_text blocks`() {
        val body = """
            {"output":[
                {"type":"web_search_call","id":"ws_1"},
                {"type":"message","content":[
                    {"type":"output_text","text":"Feria de las flores esta semana."}
                ]}
            ]}
        """.trimIndent()

        val result = AiResponseParsers.parseOpenAiResponsesResponse(body)

        assertEquals("Feria de las flores esta semana.", result.getOrNull())
    }

    @Test
    fun `openai responses api concatenates text across multiple output items`() {
        val body = """
            {"output":[
                {"type":"message","content":[{"type":"output_text","text":"NADA"}]},
                {"type":"message","content":[{"type":"output_text","text":" relevante hoy."}]}
            ]}
        """.trimIndent()

        val result = AiResponseParsers.parseOpenAiResponsesResponse(body)

        assertEquals("NADA relevante hoy.", result.getOrNull())
    }

    @Test
    fun `openai responses api missing output array fails`() {
        val body = """{"id":"resp_1"}"""

        val result = AiResponseParsers.parseOpenAiResponsesResponse(body)

        assertTrue(result.isFailure)
    }

    // ── Gemini ───────────────────────────────────────────────────────────────

    @Test
    fun `gemini response extracts single part text`() {
        val body = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"P0300 es un fallo de encendido."}]}}]}
        """.trimIndent()

        val result = AiResponseParsers.parseGeminiResponse(body)

        assertEquals("P0300 es un fallo de encendido.", result.getOrNull())
    }

    @Test
    fun `gemini response concatenates multiple parts`() {
        val body = """
            {"candidates":[{"content":{"role":"model","parts":[
                {"text":"Revisa el sensor de oxígeno."},
                {"text":" No es urgente."}
            ]}}]}
        """.trimIndent()

        val result = AiResponseParsers.parseGeminiResponse(body)

        assertEquals("Revisa el sensor de oxígeno. No es urgente.", result.getOrNull())
    }

    @Test
    fun `gemini response missing candidates fails`() {
        val body = """{"promptFeedback":{"blockReason":"SAFETY"}}"""

        val result = AiResponseParsers.parseGeminiResponse(body)

        assertTrue(result.isFailure)
    }

    @Test
    fun `gemini response with malformed json fails`() {
        val result = AiResponseParsers.parseGeminiResponse("{not valid")

        assertTrue(result.isFailure)
    }

    // ── httpErrorMessage ─────────────────────────────────────────────────────

    @Test
    fun `http error message includes api detail when body carries one`() {
        val body = """{"error":{"message":"API key not valid"}}"""

        val message = AiResponseParsers.httpErrorMessage("Gemini", 400, body)

        assertEquals("Gemini HTTP 400: API key not valid", message)
    }

    @Test
    fun `http error message with null body falls back to code only`() {
        val message = AiResponseParsers.httpErrorMessage("Gemini", 500, null)

        assertEquals("Gemini HTTP 500", message)
    }

    @Test
    fun `http error message with non json body falls back to code only`() {
        val message = AiResponseParsers.httpErrorMessage("OpenAI", 502, "<html>oops</html>")

        assertEquals("OpenAI HTTP 502", message)
    }

    @Test
    fun `http error message with json missing error key falls back to code only`() {
        val message = AiResponseParsers.httpErrorMessage("Anthropic", 404, """{"foo":1}""")

        assertEquals("Anthropic HTTP 404", message)
    }

    @Test
    fun `http error message with blank api message falls back to code only`() {
        val body = """{"error":{"message":""}}"""

        val message = AiResponseParsers.httpErrorMessage("Gemini", 429, body)

        assertEquals("Gemini HTTP 429", message)
    }

    @Test
    fun `http error message with plain string error extracts it`() {
        val body = """{"error":"model not loaded"}"""

        val message = AiResponseParsers.httpErrorMessage("Compatible OpenAI", 404, body)

        assertEquals("Compatible OpenAI HTTP 404: model not loaded", message)
    }

    @Test
    fun `http error message truncates detail to 200 chars`() {
        val longDetail = "x".repeat(250)
        val body = """{"error":{"message":"$longDetail"}}"""

        val message = AiResponseParsers.httpErrorMessage("Gemini", 400, body)

        assertEquals("Gemini HTTP 400: " + "x".repeat(200), message)
    }
}
