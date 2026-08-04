package com.revscope.core.intelligence.debrief

import com.revscope.core.intelligence.provider.AiProviderFactory
import com.revscope.core.intelligence.provider.AiRequest
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
            ?: return Result.failure(IllegalStateException("Configura un proveedor de IA en Ajustes"))
        return provider.complete(
            AiRequest(
                system = SYSTEM_PROMPT,
                user = buildUserPrompt(digest),
                maxTokens = 400,
            )
        )
    }

    private fun buildUserPrompt(d: TripDigest): String = buildString {
        appendLine("Vehículo: ${d.vehicleName} (${if (d.isMotorcycle) "moto" else "carro"})")
        appendLine("Viaje: %.1f km en %d min · vel prom %d km/h · vel máx %d km/h · RPM máx %d".format(
            d.distanceKm, d.durationMin, d.avgSpeedKmh, d.maxSpeedKmh, d.maxRpm))
        d.ecoScore?.let { appendLine("Eco-score: $it/100") }
        d.fuelCostCop?.let { appendLine("Costo combustible: %.0f COP".format(it)) }
        d.maxBrakingG?.let { appendLine("Frenada máx: %.2f G".format(it)) }
        d.maxLateralG?.let { appendLine("G lateral máx: %.2f G".format(it)) }
        d.maxLeanDeg?.takeIf { d.isMotorcycle }?.let { appendLine("Inclinación máx: %.0f°".format(it)) }
        d.best0to100s?.let { appendLine("Mejor 0-100: %.1f s".format(it)) }
        if (d.prevTripCount > 0) {
            appendLine("Historial (${d.prevTripCount} viajes previos): eco prom ${d.prevAvgEcoScore ?: "?"}, vel máx prom ${d.prevAvgMaxSpeedKmh ?: "?"} km/h")
        }
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "Eres un coach de conducción conciso y directo, en español colombiano neutro. " +
                "Con los datos del viaje: (1) resume el viaje en 2-3 frases con lo más notable, " +
                "(2) compara contra el historial si existe (¿mejor o peor que de costumbre?), " +
                "(3) da máximo 2 consejos accionables de manejo o mantenimiento. " +
                "Máximo 130 palabras. Sin listas numeradas, sin emojis, sin inventar datos que no te dieron."
    }
}
