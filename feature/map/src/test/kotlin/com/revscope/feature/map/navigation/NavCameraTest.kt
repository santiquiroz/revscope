package com.revscope.feature.map.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavCameraTest {

    @Test
    fun `parado y lejos de maniobra - zoom maximo urbano`() {
        assertEquals(17.5, NavCamera.zoom(speedKmh = 0, distToManeuverM = 5_000), 0.01)
    }

    @Test
    fun `rapido y lejos - zoom alejado`() {
        assertEquals(15.5, NavCamera.zoom(speedKmh = 90, distToManeuverM = 5_000), 0.01)
        // Más rápido no aleja más: 15.5 es el piso.
        assertEquals(15.5, NavCamera.zoom(speedKmh = 140, distToManeuverM = 5_000), 0.01)
    }

    @Test
    fun `velocidad intermedia interpola`() {
        val mid = NavCamera.zoom(speedKmh = 45, distToManeuverM = 5_000)
        assertTrue(mid > 15.5 && mid < 17.5)
    }

    @Test
    fun `acercandose a maniobra - zoom in progresivo hasta el tope`() {
        val far = NavCamera.zoom(speedKmh = 90, distToManeuverM = 300)
        val near = NavCamera.zoom(speedKmh = 90, distToManeuverM = 50)
        assertTrue(near > far)
        assertEquals(17.5, NavCamera.zoom(speedKmh = 90, distToManeuverM = 0), 0.01)
    }

    @Test
    fun `velocidad nula se trata como parado`() {
        assertEquals(17.5, NavCamera.zoom(speedKmh = null, distToManeuverM = 5_000), 0.01)
    }
}
