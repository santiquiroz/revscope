package com.revscope.core.obd.safety

/**
 * Pure state machine for motorcycle crash detection, fed by IMU acceleration
 * magnitude (G) and vehicle speed (km/h). No I/O, no coroutines — [CrashResponder]
 * owns the side effects (alarm, SMS).
 *
 * False positives are the enemy here, so every stage requires corroborating signal:
 * a raw G spike alone (dropped phone, pothole, speed bump) is not enough — it must
 * follow real speed, carry a horizontal deceleration component, collapse the speed
 * right away, and only then escalate to [State.TRIGGERED] after sustained immobility.
 */
class CrashDetector {

    enum class State { MONITORING, IMPACT_DETECTED, TRIGGERED }

    private data class SpeedSample(val timestampMs: Long, val speedKmh: Double)

    private var state = State.MONITORING
    private val speedHistory = ArrayDeque<SpeedSample>()
    private var immobileSinceMs: Long? = null
    private var impactAtMs: Long? = null
    private var speedCollapsed = false

    fun process(accelG: Double, horizontalG: Double, speedKmh: Double, nowMs: Long): State {
        recordSpeed(nowMs, speedKmh)
        when (state) {
            State.MONITORING -> evaluateImpact(accelG, horizontalG, nowMs)
            State.IMPACT_DETECTED -> evaluatePostImpact(accelG, speedKmh, nowMs)
            State.TRIGGERED -> Unit
        }
        return state
    }

    fun reset() {
        state = State.MONITORING
        speedHistory.clear()
        immobileSinceMs = null
        impactAtMs = null
        speedCollapsed = false
    }

    /**
     * True if the vehicle was moving faster than [IMPACT_MIN_SPEED_KMH] within [windowMs] of
     * [nowMs]. Used by the foreground service to decide whether a dropped OBD link deserves a
     * grace period before tearing down crash detection — a real crash severs the link too.
     */
    fun hadRecentMotion(windowMs: Long, nowMs: Long): Boolean =
        speedHistory.any { nowMs - it.timestampMs <= windowMs && it.speedKmh > IMPACT_MIN_SPEED_KMH }

    private fun recordSpeed(nowMs: Long, speedKmh: Double) {
        speedHistory.addLast(SpeedSample(nowMs, speedKmh))
        while (speedHistory.isNotEmpty() && nowMs - speedHistory.first().timestampMs > SPEED_HISTORY_RETENTION_MS) {
            speedHistory.removeFirst()
        }
    }

    private fun hadQualifyingSpeedRecently(nowMs: Long): Boolean =
        speedHistory.any { nowMs - it.timestampMs <= SPEED_HISTORY_WINDOW_MS && it.speedKmh > IMPACT_MIN_SPEED_KMH }

    /**
     * Un resalto o un hueco tomado rápido es un golpe VERTICAL: el celular en el bolsillo
     * puede pasar de 8G sin que la moto haya perdido un metro por segundo hacia adelante.
     * Un choque a más de 20 km/h convierte esa velocidad en deceleración horizontal, así que
     * se exige esa componente — salvo que el pico total sea tan alto que ningún resalto lo
     * explique (caso [CATASTROPHIC_G_THRESHOLD], p. ej. un highside que aterriza plano).
     */
    private fun isImpactSignature(accelG: Double, horizontalG: Double): Boolean {
        if (accelG > CATASTROPHIC_G_THRESHOLD) return true
        return accelG > IMPACT_G_THRESHOLD && horizontalG > IMPACT_MIN_HORIZONTAL_G
    }

    private fun evaluateImpact(accelG: Double, horizontalG: Double, nowMs: Long) {
        if (isImpactSignature(accelG, horizontalG) && hadQualifyingSpeedRecently(nowMs)) {
            state = State.IMPACT_DETECTED
            immobileSinceMs = null
            impactAtMs = nowMs
            speedCollapsed = false
        }
    }

    private fun evaluatePostImpact(accelG: Double, speedKmh: Double, nowMs: Long) {
        if (speedKmh > RECOVERY_SPEED_KMH) {
            backToMonitoring()
            return
        }
        val immobile = speedKmh < IMMOBILITY_SPEED_KMH && accelG < IMMOBILITY_ACCEL_G
        if (immobile) speedCollapsed = true

        // Un choque real detiene el vehículo de inmediato. Si sigue rodando pasada la
        // ventana, el golpe fue del camino y una parada posterior (semáforo, parqueo) no
        // puede reinterpretarse como el final de ese impacto.
        if (!speedCollapsed && elapsedSinceImpact(nowMs) > SPEED_COLLAPSE_WINDOW_MS) {
            backToMonitoring()
            return
        }

        if (!immobile) {
            immobileSinceMs = null
            return
        }
        val since = immobileSinceMs ?: nowMs.also { immobileSinceMs = it }
        if (nowMs - since >= IMMOBILITY_DURATION_MS) {
            state = State.TRIGGERED
        }
    }

    private fun elapsedSinceImpact(nowMs: Long): Long = impactAtMs?.let { nowMs - it } ?: 0L

    private fun backToMonitoring() {
        state = State.MONITORING
        immobileSinceMs = null
        impactAtMs = null
        speedCollapsed = false
    }

    companion object {
        /** Pico de aceleración total que puede indicar un impacto, en G. */
        const val IMPACT_G_THRESHOLD = 6.0

        /**
         * Componente horizontal mínima del pico para tratarlo como choque y no como golpe
         * del camino. Un resalto deja típicamente menos de 1.5G horizontales.
         */
        const val IMPACT_MIN_HORIZONTAL_G = 2.5

        /** Pico total por encima del cual se acepta el impacto sin exigir componente horizontal. */
        const val CATASTROPHIC_G_THRESHOLD = 12.0

        /** Velocidad mínima previa al impacto para considerarlo real (no una caída desde quieto). */
        const val IMPACT_MIN_SPEED_KMH = 20.0

        /** Ventana hacia atrás en la que se busca la velocidad calificante antes del impacto. */
        const val SPEED_HISTORY_WINDOW_MS = 5_000L

        /** Cuánto se retiene el historial de velocidad en memoria — cubre el lookback de 60 s de [hadRecentMotion]. */
        const val SPEED_HISTORY_RETENTION_MS = 65_000L

        /** Plazo tras el impacto dentro del cual la velocidad debe colapsar a inmovilidad. */
        const val SPEED_COLLAPSE_WINDOW_MS = 8_000L

        /** Velocidad por debajo de la cual se considera que el vehículo está inmóvil. */
        const val IMMOBILITY_SPEED_KMH = 3.0

        /** Aceleración por debajo de la cual se considera que el vehículo está inmóvil. */
        const val IMMOBILITY_ACCEL_G = 1.3

        /** Duración de inmovilidad sostenida requerida para escalar a TRIGGERED. */
        const val IMMOBILITY_DURATION_MS = 30_000L

        /** Velocidad tras el impacto que se interpreta como "sigue rodando" (falso positivo). */
        const val RECOVERY_SPEED_KMH = 10.0
    }
}
