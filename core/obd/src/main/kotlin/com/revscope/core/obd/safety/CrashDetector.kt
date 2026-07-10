package com.revscope.core.obd.safety

/**
 * Pure state machine for motorcycle crash detection, fed by IMU acceleration
 * magnitude (G) and vehicle speed (km/h). No I/O, no coroutines — [CrashResponder]
 * owns the side effects (alarm, SMS).
 *
 * False positives are the enemy here, so every stage requires corroborating signal:
 * a raw G spike alone (dropped phone, pothole) is not enough — it must follow real
 * speed, and it only escalates to [State.TRIGGERED] after sustained immobility.
 */
class CrashDetector {

    enum class State { MONITORING, IMPACT_DETECTED, TRIGGERED }

    private data class SpeedSample(val timestampMs: Long, val speedKmh: Double)

    private var state = State.MONITORING
    private val speedHistory = ArrayDeque<SpeedSample>()
    private var immobileSinceMs: Long? = null

    fun process(accelG: Double, speedKmh: Double, nowMs: Long): State {
        recordSpeed(nowMs, speedKmh)
        when (state) {
            State.MONITORING -> evaluateImpact(accelG)
            State.IMPACT_DETECTED -> evaluatePostImpact(accelG, speedKmh, nowMs)
            State.TRIGGERED -> Unit
        }
        return state
    }

    fun reset() {
        state = State.MONITORING
        speedHistory.clear()
        immobileSinceMs = null
    }

    private fun recordSpeed(nowMs: Long, speedKmh: Double) {
        speedHistory.addLast(SpeedSample(nowMs, speedKmh))
        while (speedHistory.isNotEmpty() && nowMs - speedHistory.first().timestampMs > SPEED_HISTORY_WINDOW_MS) {
            speedHistory.removeFirst()
        }
    }

    private fun hadQualifyingSpeedRecently(): Boolean =
        speedHistory.any { it.speedKmh > IMPACT_MIN_SPEED_KMH }

    private fun evaluateImpact(accelG: Double) {
        if (accelG > IMPACT_G_THRESHOLD && hadQualifyingSpeedRecently()) {
            state = State.IMPACT_DETECTED
            immobileSinceMs = null
        }
    }

    private fun evaluatePostImpact(accelG: Double, speedKmh: Double, nowMs: Long) {
        if (speedKmh > RECOVERY_SPEED_KMH) {
            state = State.MONITORING
            immobileSinceMs = null
            return
        }
        val immobile = speedKmh < IMMOBILITY_SPEED_KMH && accelG < IMMOBILITY_ACCEL_G
        if (!immobile) {
            immobileSinceMs = null
            return
        }
        val since = immobileSinceMs ?: nowMs.also { immobileSinceMs = it }
        if (nowMs - since >= IMMOBILITY_DURATION_MS) {
            state = State.TRIGGERED
        }
    }

    companion object {
        /** Pico de aceleración total que puede indicar un impacto, en G. */
        const val IMPACT_G_THRESHOLD = 6.0

        /** Velocidad mínima previa al impacto para considerarlo real (no una caída desde quieto). */
        const val IMPACT_MIN_SPEED_KMH = 20.0

        /** Ventana hacia atrás en la que se busca la velocidad calificante antes del impacto. */
        const val SPEED_HISTORY_WINDOW_MS = 5_000L

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
