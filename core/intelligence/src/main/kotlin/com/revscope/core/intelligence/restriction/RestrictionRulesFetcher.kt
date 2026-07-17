package com.revscope.core.intelligence.restriction

import com.revscope.core.intelligence.provider.AiProvider
import com.revscope.core.intelligence.provider.AiRequest
import com.revscope.core.obd.legal.PicoYPlacaEngine
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val MAX_TOKENS = 400
private const val NO_RESTRICTION_MARKER = "NONE"

sealed interface RestrictionFetchResult {
    /** [json] is the raw normalized JSON, kept for caching; [rules] the parsed result. */
    data class Rules(val json: String, val rules: PicoYPlacaEngine.CityRules) : RestrictionFetchResult

    /** The city has no plate-digit driving restriction. */
    data object None : RestrictionFetchResult

    /** No provider / no web search / API failure / unparseable answer — nothing cacheable. */
    data object Unavailable : RestrictionFetchResult
}

/**
 * Asks the configured AI provider — web search required — for the plate-digit driving
 * restriction (pico y placa, hoy no circula, rodízio…) active in an arbitrary city
 * anywhere in the world, as a compact [PicoYPlacaEngine.CityRules] JSON.
 *
 * Traffic is kept minimal on purpose: JSON-only system prompt, one short user turn,
 * hard [MAX_TOKENS] cap. Caching/cooldowns live in the caller (AiRestrictionRulesSource).
 */
class RestrictionRulesFetcher(private val providerProvider: suspend () -> AiProvider?) {

    suspend fun fetchRules(municipio: String, region: String?, pais: String?): RestrictionFetchResult {
        val provider = providerProvider() ?: return RestrictionFetchResult.Unavailable
        if (!provider.supportsWebSearch) return RestrictionFetchResult.Unavailable
        return callProvider(provider, municipio, region, pais)
    }

    private suspend fun callProvider(
        provider: AiProvider,
        municipio: String,
        region: String?,
        pais: String?,
    ): RestrictionFetchResult = try {
        val request = AiRequest(
            system = SYSTEM_PROMPT,
            user = buildPrompt(municipio, region, pais),
            maxTokens = MAX_TOKENS,
            needsWebSearch = true,
        )
        provider.complete(request).fold(
            onSuccess = { text -> parseResponse(text) },
            onFailure = { e ->
                Timber.w(e, "RestrictionRulesFetcher: API call failed for $municipio")
                RestrictionFetchResult.Unavailable
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "RestrictionRulesFetcher: API call failed for $municipio")
        RestrictionFetchResult.Unavailable
    }

    private fun buildPrompt(municipio: String, region: String?, pais: String?): String {
        val lugar = listOfNotNull(municipio, region, pais).joinToString(", ")
        return "¿Restricción vehicular por dígito de placa (pico y placa, hoy no circula, rodízio o similar) " +
            "vigente hoy ${today()} en $lugar? Si no existe o no se basa en dígitos de placa: $NO_RESTRICTION_MARKER. " +
            "Si existe, JSON exacto: $SCHEMA_HINT"
    }

    private fun today(): String = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun parseResponse(rawText: String): RestrictionFetchResult {
        val text = stripFences(rawText.trim())
        if (text.isEmpty()) return RestrictionFetchResult.Unavailable
        if (isNoRestriction(text)) return RestrictionFetchResult.None
        val rules = PicoYPlacaEngine.parseRulesJson(text) ?: return RestrictionFetchResult.Unavailable
        if (!isSane(rules)) return RestrictionFetchResult.Unavailable
        return RestrictionFetchResult.Rules(text, rules)
    }

    private fun stripFences(text: String): String = text
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    private fun isNoRestriction(text: String): Boolean {
        val normalized = text.uppercase().replace(Regex("[.,!?;:]"), "").trim()
        return normalized == NO_RESTRICTION_MARKER || normalized.startsWith("$NO_RESTRICTION_MARKER ")
    }

    private fun isSane(rules: PicoYPlacaEngine.CityRules): Boolean =
        rules.startHour in 0..23 &&
            rules.endHour in 1..24 &&
            rules.startHour < rules.endHour &&
            rules.validFromMs < rules.validUntilMs

    private companion object {
        const val SYSTEM_PROMPT =
            "Servicio de datos vehiculares. Responde SOLO un JSON de una línea o exactamente NONE. " +
                "Sin markdown, sin explicaciones."

        const val SCHEMA_HINT =
            """{"cityId":"slug","displayName":"...","scheme":"WEEKDAY_ROTATION|DATE_PARITY",""" +
                """"rotation":{"2":[dígitos lunes],...,"6":[dígitos viernes]},""" +
                """"dateParityRestricted":{"ODD_DAY":[...],"EVEN_DAY":[...]},""" +
                """"startHour":0-23,"endHour":1-24,"carDigit":"FIRST|LAST","motoDigit":"FIRST|LAST",""" +
                """"motosExentas":bool,"validFromMs":epochMs,"validUntilMs":epochMs,""" +
                """"timeZoneId":"IANA tz". Incluye solo el campo del scheme que aplique."""
    }
}
