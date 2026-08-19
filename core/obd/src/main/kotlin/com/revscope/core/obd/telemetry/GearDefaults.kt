package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.entities.VehicleType

/**
 * Única fuente de ratios default (speed*1000/rpm) por tipo. Una moto gira mucho más alto
 * por km/h que un auto, por eso su escala es ~4x menor. El learner converge desde aquí.
 */
object GearDefaults {

    private val CAR_RATIOS = listOf(12.0, 20.0, 31.0, 43.0, 56.0, 77.0, 92.0, 105.0)
    private val MOTO_RATIOS = listOf(4.0, 6.5, 9.0, 11.5, 14.0, 16.5, 19.0, 21.5)

    fun ratios(gearCount: Int, type: VehicleType): List<Double> {
        val n = gearCount.coerceIn(3, 8)
        val base = if (type == VehicleType.MOTORCYCLE) MOTO_RATIOS else CAR_RATIOS
        return base.take(n)
    }
}
