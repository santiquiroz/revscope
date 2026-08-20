package com.revscope.core.intelligence.gear

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.telemetry.GearDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveGearLearnerTest {

    private fun rpmReading(rpm: Double) = ObdReading(pid = "0C", value = rpm, unit = "rpm")

    private fun speedReading(speedKmh: Double) = ObdReading(pid = "0D", value = speedKmh, unit = "km/h")

    private fun gear1Count(learner: AdaptiveGearLearner): Int =
        learner.gearTable.value.first { it.gear == 1 }.observationCount

    @Test
    fun `reconfigure con mismos parametros no resetea observaciones`() {
        val learner = AdaptiveGearLearner(gearCount = 5, type = VehicleType.MOTORCYCLE)

        // ratio = speed*1000/rpm = 4.0*1000/1000 = 4.0, exactamente el centroide de gear 1
        // en MOTO_RATIOS — cada observación cae en el mismo cluster sin drift.
        learner.observe(rpmReading(1000.0))
        repeat(10) { learner.observe(speedReading(4.0)) }
        val observedBefore = gear1Count(learner)
        assertTrue("esperaba observaciones acumuladas antes de reconfigure", observedBefore > 0)

        // Reconexión BLE con el mismo perfil activo: start() vuelve a llamar reconfigure()
        // con los mismos parámetros — la calibración acumulada debe sobrevivir.
        learner.reconfigure(5, VehicleType.MOTORCYCLE)
        assertEquals(observedBefore, gear1Count(learner))
        assertEquals(5, learner.gearTable.value.size)

        // Cambio real de perfil (otro vehículo activado) sí debe resetear a los defaults.
        learner.reconfigure(6, VehicleType.CAR)
        val resetTable = learner.gearTable.value
        assertEquals(6, resetTable.size)
        assertTrue(resetTable.all { it.observationCount == 0 })
        assertEquals(GearDefaults.ratios(6, VehicleType.CAR), resetTable.map { it.centerRatio })
    }
}
