package com.revscope.core.obd.cameras

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCoverageTest {

    // Medellín centro
    private val centerLat = 6.2442
    private val centerLon = -75.5812

    @Test
    fun `no stored center requires refresh`() {
        assertTrue(CameraCoverage.needsRefresh(null, null, centerLat, centerLon))
        assertTrue(CameraCoverage.needsRefresh(centerLat, null, centerLat, centerLon))
        assertTrue(CameraCoverage.needsRefresh(null, centerLon, centerLat, centerLon))
    }

    @Test
    fun `inside coverage does not refresh`() {
        // Envigado ≈ 8-9 km del centro de Medellín
        assertFalse(CameraCoverage.needsRefresh(centerLat, centerLon, 6.1670, -75.5836))
    }

    @Test
    fun `far from coverage center requires refresh`() {
        // Bogotá ≈ 240 km de Medellín
        assertTrue(CameraCoverage.needsRefresh(centerLat, centerLon, 4.7110, -74.0721))
    }

    @Test
    fun `just beyond threshold requires refresh`() {
        // ~36 km al norte (0.325° latitud ≈ 36.1 km)
        assertTrue(CameraCoverage.needsRefresh(centerLat, centerLon, centerLat + 0.325, centerLon))
    }

    @Test
    fun `just inside threshold does not refresh`() {
        // ~34 km al norte (0.306° latitud ≈ 34 km)
        assertFalse(CameraCoverage.needsRefresh(centerLat, centerLon, centerLat + 0.306, centerLon))
    }
}
