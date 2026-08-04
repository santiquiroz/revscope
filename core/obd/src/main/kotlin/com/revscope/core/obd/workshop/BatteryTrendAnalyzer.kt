package com.revscope.core.obd.workshop

private const val MIN_SESSIONS = 6
private const val DECLINE_THRESHOLD_V = 0.3
private const val WEAK_CHARGING_V = 13.2

/**
 * Tendencia de salud de batería/alternador a partir del voltaje promedio por sesión
 * (VBAT con motor encendido ≈ voltaje de carga). Puro y testeable — el insumo viene
 * de TelemetryDao.recentSessionVoltages (más reciente primero).
 *
 * Señales:
 *  - Mitad reciente ≥0.3 V por debajo de la mitad vieja → sistema de carga degradándose.
 *  - Promedio reciente <13.2 V sostenido → carga débil (mismo umbral que DiagnosticRules).
 */
object BatteryTrendAnalyzer {

    enum class Verdict { OK, DEGRADANDO, CARGA_DEBIL, SIN_DATOS }

    data class Result(val verdict: Verdict, val recentAvgV: Double?, val deltaV: Double?, val detalle: String)

    fun analyze(voltagesNewestFirst: List<Double>): Result {
        if (voltagesNewestFirst.size < MIN_SESSIONS) {
            return Result(Verdict.SIN_DATOS, null, null, "Se necesitan al menos $MIN_SESSIONS viajes con voltaje")
        }
        val half = voltagesNewestFirst.size / 2
        val recent = voltagesNewestFirst.take(half)
        val older = voltagesNewestFirst.drop(half)
        val recentAvg = recent.average()
        val olderAvg = older.average()
        val delta = recentAvg - olderAvg

        return when {
            delta <= -DECLINE_THRESHOLD_V -> Result(
                Verdict.DEGRADANDO, recentAvg, delta,
                "Voltaje de carga cayó %.1f V vs tus viajes anteriores — revisa batería/alternador antes de quedar varado"
                    .format(-delta),
            )
            recentAvg < WEAK_CHARGING_V -> Result(
                Verdict.CARGA_DEBIL, recentAvg, delta,
                "Carga promedio %.1f V (esperado >13.2 V con motor encendido)".format(recentAvg),
            )
            else -> Result(
                Verdict.OK, recentAvg, delta,
                "Carga estable en %.1f V".format(recentAvg),
            )
        }
    }
}
