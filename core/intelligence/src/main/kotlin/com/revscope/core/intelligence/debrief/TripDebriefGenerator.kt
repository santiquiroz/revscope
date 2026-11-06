package com.revscope.core.intelligence.debrief

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.intelligence.R
import com.revscope.core.intelligence.provider.AiProviderFactory
import com.revscope.core.intelligence.provider.AiRequest
import com.revscope.core.intelligence.provider.withResponseLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debrief de viaje por IA: los agregados se calculan localmente (SQL/Kotlin) y el
 * modelo solo narra — una llamada corta por viaje, sin datos crudos (patrón validado
 * en research 2026-08: la mejor relación valor/token de todas las features IA).
 */
@Singleton
class TripDebriefGenerator @Inject constructor(
    private val aiProviderFactory: AiProviderFactory,
    private val resolver: StringResolver,
) {

    data class TripDigest(
        val vehicleName: String,
        val isMotorcycle: Boolean,
        val distanceKm: Double,
        val durationMin: Long,
        val avgSpeedKmh: Int,
        val maxSpeedKmh: Int,
        val maxRpm: Int,
        val ecoScore: Int?,
        val fuelCostCop: Double?,
        val maxBrakingG: Float?,
        val maxLateralG: Float?,
        val maxLeanDeg: Float?,
        val best0to100s: Double?,
        /** Promedios de los viajes anteriores del mismo vehículo, para comparar. */
        val prevTripCount: Int,
        val prevAvgEcoScore: Int?,
        val prevAvgMaxSpeedKmh: Int?,
    )

    suspend fun generate(digest: TripDigest): Result<String> {
        val provider = aiProviderFactory.current()
            ?: return Result.failure(IllegalStateException(resolver.get(R.string.ai_debrief_no_provider)))
        return provider.complete(
            AiRequest(
                system = SYSTEM_PROMPT,
                user = buildUserPrompt(digest).withResponseLanguage(resolver),
                maxTokens = 400,
            )
        )
    }

    // Cuerpo del prompt: no es texto de interfaz, se queda en español.
    private fun buildUserPrompt(d: TripDigest): String = buildString {
        appendLine("Vehículo: ${d.vehicleName} (${if (d.isMotorcycle) "moto" else "carro"})") // i18n-ignore
        appendLine("Viaje: %.1f km en %d min · vel prom %d km/h · vel máx %d km/h · RPM máx %d".format( // i18n-ignore
            d.distanceKm, d.durationMin, d.avgSpeedKmh, d.maxSpeedKmh, d.maxRpm))
        d.ecoScore?.let { appendLine("Eco-score: $it/100") } // i18n-ignore
        d.fuelCostCop?.let { appendLine("Costo combustible: %.0f COP".format(it)) } // i18n-ignore
        d.maxBrakingG?.let { appendLine("Frenada máx: %.2f G".format(it)) } // i18n-ignore
        d.maxLateralG?.let { appendLine("G lateral máx: %.2f G".format(it)) } // i18n-ignore
        d.maxLeanDeg?.takeIf { d.isMotorcycle }?.let { appendLine("Inclinación máx: %.0f°".format(it)) } // i18n-ignore
        d.best0to100s?.let { appendLine("Mejor 0-100: %.1f s".format(it)) } // i18n-ignore
        if (d.prevTripCount > 0) {
            appendLine("Historial (${d.prevTripCount} viajes previos): eco prom ${d.prevAvgEcoScore ?: "?"}, vel máx prom ${d.prevAvgMaxSpeedKmh ?: "?"} km/h") // i18n-ignore
        }
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "Eres un coach de conducción conciso y directo, de tono neutro. " + // i18n-ignore
                "Con los datos del viaje: (1) resume el viaje en 2-3 frases con lo más notable, " + // i18n-ignore
                "(2) compara contra el historial si existe (¿mejor o peor que de costumbre?), " + // i18n-ignore
                "(3) da máximo 2 consejos accionables de manejo o mantenimiento. " + // i18n-ignore
                "Máximo 130 palabras. Sin listas numeradas, sin emojis, sin inventar datos que no te dieron." // i18n-ignore
    }
}
