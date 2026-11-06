package com.revscope.core.intelligence.dtc

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.intelligence.R
import com.revscope.core.intelligence.provider.AiProvider
import com.revscope.core.intelligence.provider.AiRequest
import com.revscope.core.uitesting.XmlStringResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcExplainerTest {

    private class FakeProvider(private val response: Result<String>) : AiProvider {
        override val providerId = "fake"
        override val displayName = "Fake"
        override val supportsWebSearch = false
        var lastRequest: AiRequest? = null

        override suspend fun complete(request: AiRequest): Result<String> {
            lastRequest = request
            return response
        }
    }

    private fun promptWith(resolver: StringResolver): String = runBlocking {
        val provider = FakeProvider(Result.success("ok"))
        DtcExplainer(resolver) { provider }.explain("P0171", emptyList())
        provider.lastRequest!!.user
    }

    @Test
    fun `el prompt en ingles termina con la linea de idioma en ingles`() {
        val prompt = promptWith(XmlStringResolver.en(R::class.java))
        assertTrue(prompt, prompt.endsWith("Write your entire answer in English."))
        assertTrue(prompt.contains("Código de falla OBD-II: P0171"))
    }

    @Test
    fun `el prompt en espanol termina con la linea de idioma en espanol`() {
        val prompt = promptWith(XmlStringResolver.es(R::class.java))
        assertTrue(prompt, prompt.endsWith("Escribe toda tu respuesta en español."))
    }

    @Test
    fun `sin proveedor devuelve el texto de configuracion en espanol`() = runBlocking {
        val result = DtcExplainer(XmlStringResolver.es(R::class.java)) { null }.explain("P0171", emptyList())
        assertEquals(
            "Para obtener explicaciones con IA, configura un proveedor en Ajustes → Inteligencia artificial.",
            result.explanation,
        )
    }

    @Test
    fun `fallo del proveedor devuelve el texto de respaldo en espanol`() = runBlocking {
        val provider = FakeProvider(Result.failure(RuntimeException("boom")))
        val result = DtcExplainer(XmlStringResolver.es(R::class.java)) { provider }.explain("P0300", emptyList())
        assertEquals("Código P0300 — la explicación IA no está disponible en este momento.", result.explanation)
    }
}
