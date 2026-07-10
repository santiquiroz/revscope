package com.revscope.core.obd.cameras

import com.revscope.core.data.db.entities.SpeedCameraEntity
import org.junit.Assert.assertEquals
import org.junit.Test

private const val MEDELLIN_LAT = 6.2442
private const val MEDELLIN_LON = -75.5812

class CameraDownloadResultTest {

    private fun camera(id: Long) =
        SpeedCameraEntity(osmId = id, latitude = MEDELLIN_LAT, longitude = MEDELLIN_LON, maxSpeedKmh = null)

    @Test
    fun `counts cameras by source using the osmId sign convention`() {
        val cameras = listOf(camera(1), camera(2), camera(-1))

        val result = CameraDownloadResult.from(cameras)

        assertEquals(3, result.total)
        assertEquals(2, result.osmCount)
        assertEquals(1, result.ansvCount)
    }

    @Test
    fun `empty list produces zero counts`() {
        val result = CameraDownloadResult.from(emptyList())

        assertEquals(0, result.total)
        assertEquals(0, result.osmCount)
        assertEquals(0, result.ansvCount)
    }
}
