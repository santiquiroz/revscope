package com.revscope.core.obd.cameras

import com.revscope.core.data.db.entities.SpeedCameraEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MEDELLIN_LAT = 6.2442
private const val MEDELLIN_LON = -75.5812

class CameraDedupeTest {

    private fun camera(id: Long, lat: Double, lon: Double, maxSpeed: Int? = null) =
        SpeedCameraEntity(osmId = id, latitude = lat, longitude = lon, maxSpeedKmh = maxSpeed)

    @Test
    fun `keeps distinct cameras more than 100 meters apart`() {
        val far = camera(2, MEDELLIN_LAT + 0.01, MEDELLIN_LON) // ~1.1 km away
        val cameras = listOf(camera(1, MEDELLIN_LAT, MEDELLIN_LON), far)

        val merged = CameraDedupe.merge(cameras)

        assertEquals(2, merged.size)
    }

    @Test
    fun `collapses two sources within 100 meters into one`() {
        val osm = camera(1, MEDELLIN_LAT, MEDELLIN_LON)
        val ansv = camera(-1, MEDELLIN_LAT + 0.0003, MEDELLIN_LON) // ~33 m away

        val merged = CameraDedupe.merge(listOf(osm, ansv))

        assertEquals(1, merged.size)
    }

    @Test
    fun `prefers the duplicate that carries a maxspeed tag`() {
        val withoutSpeed = camera(1, MEDELLIN_LAT, MEDELLIN_LON, maxSpeed = null)
        val withSpeed = camera(-1, MEDELLIN_LAT + 0.0003, MEDELLIN_LON, maxSpeed = 60)

        val merged = CameraDedupe.merge(listOf(withoutSpeed, withSpeed))

        assertEquals(1, merged.size)
        assertEquals(60, merged[0].maxSpeedKmh)
    }

    @Test
    fun `does not overwrite an existing maxspeed with a duplicate lacking one`() {
        val withSpeed = camera(1, MEDELLIN_LAT, MEDELLIN_LON, maxSpeed = 60)
        val withoutSpeed = camera(-1, MEDELLIN_LAT + 0.0003, MEDELLIN_LON, maxSpeed = null)

        val merged = CameraDedupe.merge(listOf(withSpeed, withoutSpeed))

        assertEquals(1, merged.size)
        assertEquals(60, merged[0].maxSpeedKmh)
    }

    @Test
    fun `empty input produces empty output`() {
        assertTrue(CameraDedupe.merge(emptyList()).isEmpty())
    }
}
