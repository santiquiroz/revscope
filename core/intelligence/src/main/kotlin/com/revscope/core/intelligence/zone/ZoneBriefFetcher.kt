package com.revscope.core.intelligence.zone

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.intelligence.provider.AiProvider
import com.revscope.core.intelligence.provider.AiRequest
import com.revscope.core.intelligence.provider.withResponseLanguage
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_TOKENS = 320
private const val NO_INFO_MARKER = "NADA"

/**
 * Brief de conducción de zona por IA (con web_search): al llegar a un lugar nuevo —
 * en el país o en el extranjero — busca precio de combustible, peajes, restricciones
 * de circulación y, si estás fuera de tu país, qué necesita un conductor extranjero.
 * Reutiliza la misma abstracción [AiProvider] que el resto de features de IA.
 *
 * @param providerProvider lambda que devuelve el proveedor activo — :core:intelligence
 *                         no ve :core:data (mismo motivo que DtcExplainer/LocalInfoFetcher).
 * @param resolver resuelve la línea final con el idioma de respuesta.
 */
class ZoneBriefFetcher(
    private val resolver: StringResolver,
    private val providerProvider: suspend () -> AiProvider?,
) {

    /** Cuerpo del brief (viñetas) o null ante nada útil / sin web search / error. */
    suspend fun fetch(municipio: String, departamento: String?, pais: String?, homeCountry: String): String? {
        val provider = providerProvider() ?: return null
        if (!provider.supportsWebSearch) return null
        return callProvider(provider, municipio, departamento, pais, homeCountry)
    }

    private suspend fun callProvider(
        provider: AiProvider,
        municipio: String,
        departamento: String?,
        pais: String?,
        homeCountry: String,
    ): String? = try {
        provider.complete(
            AiRequest(
                user = buildPrompt(municipio, departamento, pais, homeCountry).withResponseLanguage(resolver),
                maxTokens = MAX_TOKENS,
                needsWebSearch = true,
            )
        ).fold(
            onSuccess = { parseBrief(it) },
            onFailure = { e -> Timber.w(e, "ZoneBriefFetcher: fallo API para $municipio"); null },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "ZoneBriefFetcher: fallo API para $municipio")
        null
    }

    private fun buildPrompt(municipio: String, departamento: String?, pais: String?, homeCountry: String): String {
        val lugar = listOfNotNull(municipio, departamento, pais).joinToString(", ")
        val paisActual = pais?.trim().orEmpty()
        val extranjero = paisActual.isNotBlank() && !paisActual.equals(homeCountry, ignoreCase = true)
        // Cuerpo del prompt: no es texto de interfaz, se queda en español.
        val docLine = if (extranjero) {
            "- 📄 Qué necesita un conductor extranjero para manejar aquí (documentos, lado de la vía, normas o límites notables)." // i18n-ignore
        } else {
            "- 📄 Cualquier requisito o norma de tránsito notable de esta zona." // i18n-ignore
        }
        val header = if (extranjero) {
            "Soy de $homeCountry y acabo de llegar conduciendo a $lugar (soy conductor extranjero aquí)." // i18n-ignore
        } else {
            "Acabo de llegar conduciendo a $lugar." // i18n-ignore
        }
        return buildString {
            append("Hoy es ${today()}. $header ") // i18n-ignore
            appendLine("Dame un brief CORTO y práctico de conducción para esta zona, en viñetas de una línea, cubriendo SOLO lo que aplique y encuentres actual:") // i18n-ignore
            appendLine("- ⛽ Precio actual del combustible aquí (con la moneda local).") // i18n-ignore
            appendLine("- 🛣️ Peajes en las vías principales de la zona (valor aproximado si lo sabes).") // i18n-ignore
            appendLine("- 🚦 Restricción de circulación vigente hoy (pico y placa, zona de bajas emisiones, etc.).") // i18n-ignore
            appendLine(docLine)
            append("Máximo 5 viñetas, cada una ≤15 palabras, sin introducción. Si no encuentras nada útil y actual, responde exactamente: $NO_INFO_MARKER") // i18n-ignore
        }
    }

    private fun today(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es"))) // i18n-ignore: fecha del cuerpo del prompt

    private fun parseBrief(rawText: String): String? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        val normalized = text.uppercase().replace(Regex("[.,!?;:]"), "").trim()
        if (normalized == NO_INFO_MARKER || normalized.startsWith("$NO_INFO_MARKER ")) return null
        return text
    }
}
