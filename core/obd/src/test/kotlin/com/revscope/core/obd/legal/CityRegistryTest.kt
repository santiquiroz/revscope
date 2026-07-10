package com.revscope.core.obd.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CityRegistryTest {

    @Test
    fun `nearest encuentra medellin a 5km de su centro`() {
        val city = CityRegistry.nearest(MEDELLIN_5KM_NORTH_LAT, MEDELLIN_LON)

        assertEquals("medellin", city?.id)
    }

    @Test
    fun `nearest no encuentra ninguna ciudad a 30km del centro de medellin`() {
        val city = CityRegistry.nearest(MEDELLIN_30KM_NORTH_LAT, MEDELLIN_LON)

        assertNull(city)
    }

    @Test
    fun `registro incluye medellin con las reglas del semestre vigente`() {
        val medellin = CityRegistry.CITIES.first { it.id == "medellin" }

        assertEquals(PicoYPlacaEngine.MEDELLIN_2026_S1, medellin.rules)
    }

    @Test
    fun `registro incluye bogota con las reglas de par y impar`() {
        val bogota = CityRegistry.CITIES.first { it.id == "bogota" }

        assertEquals(PicoYPlacaEngine.BOGOTA_2026, bogota.rules)
    }

    @Test
    fun `registro incluye cali sin reglas configuradas`() {
        val cali = CityRegistry.CITIES.first { it.id == "cali" }

        assertNull(cali.rules)
    }

    private companion object {
        const val MEDELLIN_LON = -75.5812
        const val MEDELLIN_5KM_NORTH_LAT = 6.2891660181862274
        const val MEDELLIN_30KM_NORTH_LAT = 6.513996109117362
    }
}
