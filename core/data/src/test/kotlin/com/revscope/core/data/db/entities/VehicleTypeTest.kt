package com.revscope.core.data.db.entities

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleTypeTest {

    @Test
    fun `MOTORCYCLE parsea exacto`() {
        assertEquals(VehicleType.MOTORCYCLE, VehicleType.from("MOTORCYCLE"))
    }

    @Test
    fun `CAR parsea exacto`() {
        assertEquals(VehicleType.CAR, VehicleType.from("CAR"))
    }

    @Test
    fun `null y basura caen a CAR`() {
        assertEquals(VehicleType.CAR, VehicleType.from(null))
        assertEquals(VehicleType.CAR, VehicleType.from(""))
        assertEquals(VehicleType.CAR, VehicleType.from("TRUCK"))
        assertEquals(VehicleType.CAR, VehicleType.from("motorcycle"))
    }
}
