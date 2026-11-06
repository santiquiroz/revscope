package com.revscope.core.obd.telemetry.captura

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
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

    fun decidir(e: EstadoDispositivo, maxDuracionMs: Long, resolver: StringResolver): DecisionSalvaguarda = when {
        e.transcurridoMs >= maxDuracionMs ->
            DecisionSalvaguarda.Detener(resolver.get(R.string.obd_capture_max_duration))
        bateriaBajo(e, BATERIA_DETENER_PCT) ->
            DecisionSalvaguarda.Detener(resolver.get(R.string.obd_capture_low_battery))
        (e.termico ?: 0) >= TERMICO_SEVERO ->
            DecisionSalvaguarda.Detener(resolver.get(R.string.obd_capture_phone_hot))
        adaptadorNoSostiene(e) -> DecisionSalvaguarda.Detener(
            resolver.get(R.string.obd_capture_adapter_overloaded),
        )
        (e.termico ?: 0) >= TERMICO_MODERADO || bateriaBajo(e, BATERIA_LIMITAR_PCT) ->
            DecisionSalvaguarda.Limitar(HZ_LIMITADO)
        else -> DecisionSalvaguarda.Continuar
    }

    /** Mismas reglas de batería y calor para el preset Máximo del sondeo normal (sin tope de duración ni de errores). */
    fun decidirMuestreo(l: LecturaDispositivo): DecisionSalvaguarda {
        val estado = EstadoDispositivo(l.bateriaPct, l.cargando, l.termico, 0L, 0.0, 0)
        return when {
            bateriaBajo(estado, BATERIA_DETENER_PCT) || (estado.termico ?: 0) >= TERMICO_SEVERO ->
                DecisionSalvaguarda.Detener("safety_stop")
            (estado.termico ?: 0) >= TERMICO_MODERADO || bateriaBajo(estado, BATERIA_LIMITAR_PCT) ->
                DecisionSalvaguarda.Limitar(HZ_LIMITADO)
            else -> DecisionSalvaguarda.Continuar
        }
    }

    private fun bateriaBajo(e: EstadoDispositivo, umbral: Int): Boolean =
        !e.cargando && e.bateriaPct != null && e.bateriaPct < umbral

    private fun adaptadorNoSostiene(e: EstadoDispositivo): Boolean =
        e.peticiones >= MIN_PETICIONES_PARA_RATIO && e.ratioErrores > MAX_RATIO_ERRORES
}
