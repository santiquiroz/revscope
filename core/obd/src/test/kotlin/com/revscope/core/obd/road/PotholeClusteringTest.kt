package com.revscope.core.obd.road

import com.revscope.core.data.db.entities.PotholeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PotholeClusteringTest {

    private val base = PotholeEntity(
        id = 1, latitude = 6.2442, longitude = -75.5812,
        severityG = 3.0f, hits = 1, lastHitAt = 1_000L,
    )

    @Test
    fun `golpe a menos de 30m encuentra el hueco existente`() {
        // ~11 m al norte
        val found = PotholeClustering.findNearby(6.2443, -75.5812, listOf(base))
        assertNotNull(found)
    }

    @Test
    fun `golpe a mas de 30m no matchea`() {
        // ~110 m al norte
        assertNull(PotholeClustering.findNearby(6.2452, -75.5812, listOf(base)))
    }

    @Test
    fun `refuerzo incrementa hits y conserva la peor severidad`() {
        val reinforced = PotholeClustering.reinforced(base, severityG = 2.6f, nowMs = 2_000L)
        assertEquals(2, reinforced.hits)
        assertEquals(3.0f, reinforced.severityG)
        assertEquals(2_000L, reinforced.lastHitAt)

        val worse = PotholeClustering.reinforced(base, severityG = 4.2f, nowMs = 3_000L)
        assertEquals(4.2f, worse.severityG)
    }
}
