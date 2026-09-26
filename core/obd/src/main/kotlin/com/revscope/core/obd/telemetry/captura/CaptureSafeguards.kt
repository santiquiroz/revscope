package com.revscope.core.obd.telemetry.captura

/** Lo que se mide del teléfono y de la captura en cada evaluación (cada 1 s). */
data class EstadoDispositivo(
    val bateriaPct: Int?,
    val cargando: Boolean,
    val termico: Int?,
    val transcurridoMs: Long,
    val ratioErrores: Double,
    val peticiones: Int,
)

sealed interface DecisionSalvaguarda {
    data object Continuar : DecisionSalvaguarda
    data class Limitar(val maxHz: Int) : DecisionSalvaguarda
    data class Detener(val motivo: String) : DecisionSalvaguarda
}

/** Salvaguardas de batería, calor, duración y adaptador. Reglas en orden de prioridad. */
object CaptureSafeguards {

    const val BATERIA_DETENER_PCT = 15
    const val BATERIA_LIMITAR_PCT = 30
    const val HZ_LIMITADO = 10
    const val MAX_RATIO_ERRORES = 0.3
    const val MIN_PETICIONES_PARA_RATIO = 50

    // PowerManager.THERMAL_STATUS_MODERATE / SEVERE (API 29+; null en 26-28).
    const val TERMICO_MODERADO = 2
    const val TERMICO_SEVERO = 3

    fun decidir(e: EstadoDispositivo, maxDuracionMs: Long): DecisionSalvaguarda = when {
        e.transcurridoMs >= maxDuracionMs -> DecisionSalvaguarda.Detener("tiempo máximo")
        bateriaBajo(e, BATERIA_DETENER_PCT) -> DecisionSalvaguarda.Detener("batería del teléfono por debajo de 15 %")
        (e.termico ?: 0) >= TERMICO_SEVERO -> DecisionSalvaguarda.Detener("el teléfono está muy caliente")
        adaptadorNoSostiene(e) -> DecisionSalvaguarda.Detener(
            "el adaptador no sostiene la captura; se volvió al muestreo normal",
        )
        (e.termico ?: 0) >= TERMICO_MODERADO || bateriaBajo(e, BATERIA_LIMITAR_PCT) ->
            DecisionSalvaguarda.Limitar(HZ_LIMITADO)
        else -> DecisionSalvaguarda.Continuar
    }

    /** Mismas reglas de batería y calor para el preset Máximo del sondeo normal (sin tope de duración ni de errores). */
    fun decidirMuestreo(l: LecturaDispositivo): DecisionSalvaguarda =
        decidir(EstadoDispositivo(l.bateriaPct, l.cargando, l.termico, 0L, 0.0, 0), Long.MAX_VALUE)

    private fun bateriaBajo(e: EstadoDispositivo, umbral: Int): Boolean =
        !e.cargando && e.bateriaPct != null && e.bateriaPct < umbral

    private fun adaptadorNoSostiene(e: EstadoDispositivo): Boolean =
        e.peticiones >= MIN_PETICIONES_PARA_RATIO && e.ratioErrores > MAX_RATIO_ERRORES
}
