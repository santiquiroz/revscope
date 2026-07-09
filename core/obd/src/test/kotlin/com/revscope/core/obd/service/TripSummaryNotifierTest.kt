package com.revscope.core.obd.service

import com.revscope.core.data.db.entities.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSummaryNotifierTest {

    private fun session(distanceKm: Float, maxSpeed: Int, durationMin: Long) = SessionEntity(
        id = 1L, vehicleProfileId = 0L,
        startedAt = 1_000_000L, endedAt = 1_000_000L + durationMin * 60_000,
        adapterName = "vLinker", maxRpm = 9000, maxSpeed = maxSpeed, distanceKm = distanceKm,
    )

    @Test
    fun `resumen con distancia velocidad y duracion`() {
        val text = TripSummaryNotifier.summaryText(session(23.4f, 82, 34))
        assertEquals("23,4 km · 82 km/h máx · 34 min", text)
    }

    @Test
    fun `viaje real se notifica`() {
        assertTrue(TripSummaryNotifier.shouldNotify(session(5.2f, 60, 12)))
    }

    @Test
    fun `prueba de garaje no se notifica`() {
        assertFalse(TripSummaryNotifier.shouldNotify(session(0.05f, 0, 3)))
    }
}
