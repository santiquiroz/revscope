package com.revscope.core.obd.service

import com.revscope.core.data.db.entities.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSummaryNotifierTest {

    private fun session(
        distanceKm: Float,
        maxSpeed: Int,
        durationMin: Long,
        fuelCostCop: Double? = null,
        ecoScore: Int? = null,
    ) = SessionEntity(
        id = 1L, vehicleProfileId = 0L,
        startedAt = 1_000_000L, endedAt = 1_000_000L + durationMin * 60_000,
        adapterName = "vLinker", maxRpm = 9000, maxSpeed = maxSpeed, distanceKm = distanceKm,
        fuelCostCop = fuelCostCop, ecoScore = ecoScore,
    )

    @Test
    fun `resumen con distancia velocidad y duracion`() {
        val text = TripSummaryNotifier.summaryText(session(23.4f, 82, 34))
        assertEquals("23,4 km · 82 km/h máx · 34 min", text)
    }

    @Test
    fun `resumen agrega costo de combustible cuando esta disponible`() {
        val text = TripSummaryNotifier.summaryText(session(23.4f, 82, 34, fuelCostCop = 4_200.0))
        assertEquals("23,4 km · 82 km/h máx · 34 min · $4.200", text)
    }

    @Test
    fun `resumen agrega eco score cuando esta disponible`() {
        val text = TripSummaryNotifier.summaryText(session(23.4f, 82, 34, ecoScore = 85))
        assertEquals("23,4 km · 82 km/h máx · 34 min · Eco 85", text)
    }

    @Test
    fun `resumen agrega costo y eco score juntos en orden`() {
        val text = TripSummaryNotifier.summaryText(
            session(23.4f, 82, 34, fuelCostCop = 4_200.0, ecoScore = 85),
        )
        assertEquals("23,4 km · 82 km/h máx · 34 min · $4.200 · Eco 85", text)
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
