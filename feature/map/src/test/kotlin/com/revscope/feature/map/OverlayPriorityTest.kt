package com.revscope.feature.map

import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.social.RoomClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPriorityTest {

    private val radar = SpeedCameraAlerter.ApproachingCamera(
        osmId = 1L,
        latitude = 6.2,
        longitude = -75.6,
        distanceM = 300,
        maxSpeedKmh = 60,
    )
    private val sharedDest = RoomClient.SharedDest(rider = "Nico", lat = 6.2, lon = -75.6, name = "Parque")

    @Test
    fun `mapa corrupto gana sobre todo lo demas`() {
        val result = pickSecondaryBanner(
            mapCorruptedMessage = "corrupto",
            navigationErrorMessage = "error nav",
            approachingRadar = radar,
            incomingSharedDest = sharedDest,
        )
        assertTrue(result is SecondaryBanner.MapCorrupted)
        assertEquals("corrupto", (result as SecondaryBanner.MapCorrupted).message)
    }

    @Test
    fun `error de navegacion gana sobre radar y destino compartido`() {
        val result = pickSecondaryBanner(
            mapCorruptedMessage = null,
            navigationErrorMessage = "error nav",
            approachingRadar = radar,
            incomingSharedDest = sharedDest,
        )
        assertTrue(result is SecondaryBanner.NavError)
        assertEquals("error nav", (result as SecondaryBanner.NavError).message)
    }

    @Test
    fun `radar gana sobre destino compartido`() {
        val result = pickSecondaryBanner(
            mapCorruptedMessage = null,
            navigationErrorMessage = null,
            approachingRadar = radar,
            incomingSharedDest = sharedDest,
        )
        assertTrue(result is SecondaryBanner.Radar)
        assertEquals(radar, (result as SecondaryBanner.Radar).target)
    }

    @Test
    fun `destino compartido solo cuando no hay nada mas urgente`() {
        val result = pickSecondaryBanner(
            mapCorruptedMessage = null,
            navigationErrorMessage = null,
            approachingRadar = null,
            incomingSharedDest = sharedDest,
        )
        assertTrue(result is SecondaryBanner.SharedDest)
        assertEquals(sharedDest, (result as SecondaryBanner.SharedDest).dest)
    }

    @Test
    fun `sin ninguna senal no hay banner`() {
        val result = pickSecondaryBanner(
            mapCorruptedMessage = null,
            navigationErrorMessage = null,
            approachingRadar = null,
            incomingSharedDest = null,
        )
        assertNull(result)
    }

    @Test
    fun `promo de mapa remoto solo cuando no hay nada mas urgente`() {
        val result = pickSecondaryBanner(
            mapCorruptedMessage = null,
            navigationErrorMessage = null,
            approachingRadar = null,
            incomingSharedDest = null,
            showRemoteMapPromo = true,
        )
        assertTrue(result is SecondaryBanner.RemoteMapPromo)
    }

    @Test
    fun `destino compartido gana sobre la promo de mapa remoto`() {
        val result = pickSecondaryBanner(
            mapCorruptedMessage = null,
            navigationErrorMessage = null,
            approachingRadar = null,
            incomingSharedDest = sharedDest,
            showRemoteMapPromo = true,
        )
        assertTrue(result is SecondaryBanner.SharedDest)
    }

    @Test
    fun `sin promo de mapa remoto y sin nada mas no hay banner`() {
        val result = pickSecondaryBanner(
            mapCorruptedMessage = null,
            navigationErrorMessage = null,
            approachingRadar = null,
            incomingSharedDest = null,
            showRemoteMapPromo = false,
        )
        assertNull(result)
    }
}
