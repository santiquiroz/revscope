package com.revscope.core.obd.workshop

import kotlin.math.abs

/**
 * Decides whether a fresh ECU odometer reading (PID 01 A6) should overwrite the app
 * odometer (vehicle_profiles.odometerBaseKm). The ECU is the authoritative source;
 * syncing is skipped only when the reading smells wrong — rollback diagnosis, zero/garbage
 * value, or fewer km than the app already recorded in sessions — or when the write would
 * be a no-op.
 */
object OdometerAutoSync {

    const val DELTA_MINIMA_KM = 1.0

    fun nuevaBase(ecuKm: Double, sumaSesionesKm: Double): Double = ecuKm - sumaSesionesKm

    fun debeSincronizar(
        ecuKm: Double,
        odometroAppKm: Double,
        sumaSesionesKm: Double,
        esFalla: Boolean,
    ): Boolean {
        if (esFalla) return false
        if (ecuKm <= 0.0) return false
        if (ecuKm < sumaSesionesKm) return false
        return abs(ecuKm - odometroAppKm) >= DELTA_MINIMA_KM
    }
}
