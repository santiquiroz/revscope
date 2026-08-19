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
class CrashDetector(private val thresholds: CrashThresholds = CrashThresholds.MOTORCYCLE) {

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
     * True if the vehicle was moving faster than the configured minimum impact speed within
     * [windowMs] of [nowMs]. Used by the foreground service to decide whether a dropped OBD link
     * deserves a grace period before tearing down crash detection — a real crash severs the link too.
     */
    fun hadRecentMotion(windowMs: Long, nowMs: Long): Boolean =
        speedHistory.any { nowMs - it.timestampMs <= windowMs && it.speedKmh > thresholds.impactMinSpeedKmh }

    private fun recordSpeed(nowMs: Long, speedKmh: Double) {
        speedHistory.addLast(SpeedSample(nowMs, speedKmh))
        while (speedHistory.isNotEmpty() && nowMs - speedHistory.first().timestampMs > SPEED_HISTORY_RETENTION_MS) {
            speedHistory.removeFirst()
        }
    }

    private fun hadQualifyingSpeedRecently(nowMs: Long): Boolean =
        speedHistory.any { nowMs - it.timestampMs <= SPEED_HISTORY_WINDOW_MS && it.speedKmh > thresholds.impactMinSpeedKmh }

    /**
     * Un resalto o un hueco tomado rápido es un golpe VERTICAL: el celular en el bolsillo
     * puede pasar de 8G sin que la moto haya perdido un metro por segundo hacia adelante.
     * Un choque a más de 20 km/h convierte esa velocidad en deceleración horizontal, así que
     * se exige esa componente — salvo que el pico total sea tan alto que ningún resalto lo
     * explique (caso del umbral catastrófico, p. ej. un highside que aterriza plano).
     */
    private fun isImpactSignature(accelG: Double, horizontalG: Double): Boolean {
        if (accelG > thresholds.catastrophicG) return true
        return accelG > thresholds.impactG && horizontalG > thresholds.impactMinHorizontalG
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
        if (speedKmh > thresholds.recoverySpeedKmh) {
            backToMonitoring()
            return
        }
        val immobile = speedKmh < thresholds.immobilitySpeedKmh && accelG < thresholds.immobilityAccelG
        if (immobile) speedCollapsed = true

        // Un choque real detiene el vehículo de inmediato. Si sigue rodando pasada la
        // ventana, el golpe fue del camino y una parada posterior (semáforo, parqueo) no
        // puede reinterpretarse como el final de ese impacto.
        if (!speedCollapsed && elapsedSinceImpact(nowMs) > thresholds.speedCollapseWindowMs) {
            backToMonitoring()
            return
        }

        if (!immobile) {
            immobileSinceMs = null
            return
        }
        val since = immobileSinceMs ?: nowMs.also { immobileSinceMs = it }
        if (nowMs - since >= thresholds.immobilityDurationMs) {
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
        /** Ventana hacia atrás en la que se busca la velocidad calificante antes del impacto. */
        const val SPEED_HISTORY_WINDOW_MS = 5_000L

        /** Cuánto se retiene el historial de velocidad en memoria — cubre el lookback de 60 s de [hadRecentMotion]. */
        const val SPEED_HISTORY_RETENTION_MS = 65_000L
    }
}
