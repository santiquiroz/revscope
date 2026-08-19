package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.entities.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GearDefaultsTest {

    @Test
    fun `auto 6 marchas devuelve la tabla historica`() {
        assertEquals(listOf(12.0, 20.0, 31.0, 43.0, 56.0, 77.0), GearDefaults.ratios(6, VehicleType.CAR))
    }

    @Test
    fun `moto 5 marchas devuelve 5 ratios crecientes y menores que los de auto`() {
        val moto = GearDefaults.ratios(5, VehicleType.MOTORCYCLE)
        assertEquals(5, moto.size)
        assertTrue(moto.zipWithNext().all { (a, b) -> a < b })
        assertTrue(moto.last() < 20.0)
    }

    @Test
    fun `gearCount fuera de rango se coerce a 3-8`() {
        assertEquals(3, GearDefaults.ratios(1, VehicleType.CAR).size)
        assertEquals(8, GearDefaults.ratios(12, VehicleType.CAR).size)
    }
}
