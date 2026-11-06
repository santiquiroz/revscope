package com.revscope.core.intelligence.local

import com.revscope.core.intelligence.R
import com.revscope.core.intelligence.provider.AiProvider
import com.revscope.core.intelligence.provider.AiRequest
import com.revscope.core.uitesting.XmlStringResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInfoFetcherTest {

    private class FakeProvider(private val response: Result<String>) : AiProvider {
        override val providerId = "fake"
        override val displayName = "Fake"
        override val supportsWebSearch = true
        var lastRequest: AiRequest? = null

        override suspend fun complete(request: AiRequest): Result<String> {
            lastRequest = request
            return response
        }
    }

    private fun fetcher(provider: AiProvider, english: Boolean = false) = LocalInfoFetcher(
        if (english) XmlStringResolver.en(R::class.java) else XmlStringResolver.es(R::class.java),
    ) { provider }

    @Test
    fun `NADA sigue significando sin informacion aunque el idioma sea ingles`() = runBlocking {
        assertNull(fetcher(FakeProvider(Result.success("NADA")), english = true).fetchLocalInfo("Rionegro", null))
        assertNull(fetcher(FakeProvider(Result.success("Nada.")), english = true).fetchLocalInfo("Rionegro", null))
    }

    @Test
    fun `una frase real se devuelve tal cual`() = runBlocking {
        val frase = "Hoy hay feria de las flores en el centro."
        assertEquals(frase, fetcher(FakeProvider(Result.success(frase))).fetchLocalInfo("Medellín", "Antioquia"))
    }

    @Test
    fun `el prompt conserva el centinela y termina con la linea de idioma`() = runBlocking {
        val provider = FakeProvider(Result.success("NADA"))
        fetcher(provider, english = true).fetchLocalInfo("Rionegro", "Antioquia")
        val prompt = provider.lastRequest!!.user
        assertTrue(prompt.contains("responde exactamente: NADA"))
        assertTrue(prompt, prompt.endsWith("Write your entire answer in English."))
    }
}
