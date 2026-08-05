package com.revscope.core.obd.road

import kotlin.math.abs

/**
 * Decisión instantánea del guardián wet-lean, pura y testeable. El estado (sostenido
 * en el tiempo, cooldown) lo maneja [WetLeanGuard]; esto solo dice si en ESTE instante
 * las condiciones califican como "inclinación peligrosa en curva real bajo lluvia".
 *
 * La corroboración por G lateral es lo que descarta el teléfono suelto en el bolsillo:
 * inclinarse en una curva jala aceleración lateral sostenida; un bolsillo que se mece
 * cambia el ángulo de lean sin G lateral real (la G lateral es en marco mundo, no
 * depende de cómo esté orientado el teléfono).
 */
object WetLeanDecision {

    const val MIN_SPEED_KMH = 25f
    const val MIN_THRESHOLD_DEG = 30f
    const val MIN_LATERAL_G = 0.20f
    const val WET_LEAN_FACTOR = 0.75f

    /** Umbral efectivo: 30° o el 75% del máximo personal en seco, lo que sea mayor. */
    fun threshold(dryMaxLeanDeg: Float): Float =
        maxOf(MIN_THRESHOLD_DEG, dryMaxLeanDeg * WET_LEAN_FACTOR)

    fun qualifies(
        rainActive: Boolean,
        calibrated: Boolean,
        speedKmh: Float,
        leanDeg: Float,
        lateralG: Float,
        dryMaxLeanDeg: Float,
    ): Boolean {
        if (!rainActive) return false
        // Sin baseline de calibración el lean no significa nada (típico en bolsillo).
        if (!calibrated) return false
        if (speedKmh < MIN_SPEED_KMH) return false
        if (leanDeg < threshold(dryMaxLeanDeg)) return false
        // La curva real jala G lateral; el bolsillo meciéndose no.
        if (abs(lateralG) < MIN_LATERAL_G) return false
        return true
    }
}
