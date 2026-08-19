package com.revscope.core.obd.safety

import com.revscope.core.data.db.entities.VehicleType

/**
 * Umbrales de detección de accidente por tipo de vehículo. Los valores de moto son los
 * originales del detector (diseñados para caída/highside); el set de auto arranca con el
 * mismo esqueleto — sin supuestos de caída lateral — y queda tuneable por separado.
 */
data class CrashThresholds(
    val impactG: Double,
    val impactMinHorizontalG: Double,
    val catastrophicG: Double,
    val impactMinSpeedKmh: Double,
    val speedCollapseWindowMs: Long,
    val immobilitySpeedKmh: Double,
    val immobilityAccelG: Double,
    val immobilityDurationMs: Long,
    val recoverySpeedKmh: Double,
) {
    companion object {
        val MOTORCYCLE = CrashThresholds(
            impactG = 6.0,
            impactMinHorizontalG = 2.5,
            catastrophicG = 12.0,
            impactMinSpeedKmh = 20.0,
            speedCollapseWindowMs = 8_000L,
            immobilitySpeedKmh = 3.0,
            immobilityAccelG = 1.3,
            immobilityDurationMs = 30_000L,
            recoverySpeedKmh = 10.0,
        )

        // Un choque de auto llega amortiguado por la carrocería al teléfono montado:
        // umbral de impacto algo menor y sin el sesgo de caída lateral en el copy.
        val CAR = MOTORCYCLE.copy(impactG = 5.0)

        fun forType(type: VehicleType): CrashThresholds =
            if (type == VehicleType.MOTORCYCLE) MOTORCYCLE else CAR
    }
}
