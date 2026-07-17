package com.revscope.core.intelligence.restriction

import com.revscope.core.intelligence.provider.AiProvider
import com.revscope.core.intelligence.provider.AiRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionRulesFetcherTest {

    private class FakeProvider(
        private val response: Result<String>,
        override val supportsWebSearch: Boolean = true,
    ) : AiProvider {
        override val providerId = "fake"
        override val displayName = "Fake"
        var lastRequest: AiRequest? = null

        override suspend fun complete(request: AiRequest): Result<String> {
            lastRequest = request
            return response
        }
    }

    private val validJson = """
        {"cityId":"cdmx","displayName":"Ciudad de México","scheme":"WEEKDAY_ROTATION",
         "rotation":{"2":[5,6],"3":[7,8],"4":[3,4],"5":[1,2],"6":[9,0]},
         "startHour":5,"endHour":22,"carDigit":"LAST","motoDigit":"LAST","motosExentas":false,
         "validFromMs":0,"validUntilMs":9999999999999,"timeZoneId":"America/Mexico_City"}
    """.trimIndent()

    private fun fetcher(provider: AiProvider?) = RestrictionRulesFetcher { provider }

    @Test
    fun `respuesta NONE significa ciudad sin restriccion`() = runBlocking {
        val result = fetcher(FakeProvider(Result.success("NONE"))).fetchRules("Melbourne", "Victoria", "Australia")
        assertEquals(RestrictionFetchResult.None, result)
    }

    @Test
    fun `json valido produce reglas parseadas`() = runBlocking {
        val result = fetcher(FakeProvider(Result.success(validJson))).fetchRules("CDMX", null, "México")
        val rules = (result as RestrictionFetchResult.Rules).rules
        assertEquals("Ciudad de México", rules.displayName)
        assertEquals("America/Mexico_City", rules.timeZoneId)
    }

    @Test
    fun `json envuelto en fences markdown tambien parsea`() = runBlocking {
        val fenced = "```json\n$validJson\n```"
        val result = fetcher(FakeProvider(Result.success(fenced))).fetchRules("CDMX", null, "México")
        assertTrue(result is RestrictionFetchResult.Rules)
    }

    @Test
    fun `respuesta basura es unavailable`() = runBlocking {
        val result = fetcher(FakeProvider(Result.success("no tengo idea"))).fetchRules("CDMX", null, null)
        assertEquals(RestrictionFetchResult.Unavailable, result)
    }

    @Test
    fun `horas fuera de rango invalidan las reglas`() = runBlocking {
        val badHours = validJson.replace("\"startHour\":5", "\"startHour\":25")
        val result = fetcher(FakeProvider(Result.success(badHours))).fetchRules("CDMX", null, null)
        assertEquals(RestrictionFetchResult.Unavailable, result)
    }

    @Test
    fun `proveedor sin web search es unavailable sin llamar la api`() = runBlocking {
        val provider = FakeProvider(Result.success(validJson), supportsWebSearch = false)
        val result = fetcher(provider).fetchRules("CDMX", null, null)
        assertEquals(RestrictionFetchResult.Unavailable, result)
        assertNull(provider.lastRequest)
    }

    @Test
    fun `sin proveedor configurado es unavailable`() = runBlocking {
        val result = fetcher(null).fetchRules("CDMX", null, null)
        assertEquals(RestrictionFetchResult.Unavailable, result)
    }

    @Test
    fun `la peticion pide web search con presupuesto de tokens acotado`() = runBlocking {
        val provider = FakeProvider(Result.success("NONE"))
        fetcher(provider).fetchRules("Medellín", "Antioquia", "Colombia")
        val request = provider.lastRequest!!
        assertTrue(request.needsWebSearch)
        assertTrue(request.maxTokens <= 500)
        assertTrue(request.user.contains("Medellín, Antioquia, Colombia"))
    }

    @Test
    fun `fallo del proveedor es unavailable`() = runBlocking {
        val result = fetcher(FakeProvider(Result.failure(RuntimeException("boom")))).fetchRules("CDMX", null, null)
        assertEquals(RestrictionFetchResult.Unavailable, result)
    }
}
