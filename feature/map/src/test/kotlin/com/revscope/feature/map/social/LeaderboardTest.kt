package com.revscope.feature.map.social

import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.social.RoomClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardTest {

    private val dest = RoomClient.SharedDest(rider = "ana", lat = 0.0, lon = 0.0, name = "Panadería")

    private fun peerAt(name: String, dLatDeg: Double, speedKmh: Double?): RoomClient.Peer =
        RoomClient.Peer(
            rider = name,
            lat = dLatDeg,
            lon = 0.0,
            speedKmh = speedKmh,
            headingDeg = null,
            seenAtMs = 0L,
        )

    @Test
    fun `ordena por restante ascendente entre los que no llegaron`() {
        val near = peerAt("near", 0.001, 40.0) // ~111 m
        val far = peerAt("far", 0.003, 40.0) // ~334 m

        val entries = RankingCalc.rank(self = null, selfSpeedKmh = null, peers = listOf(far, near), dest = dest)

        assertEquals(listOf("near", "far"), entries.map { it.name })
    }

    @Test
    fun `llegados quedan primero en orden de insercion, no por distancia`() {
        // "dos" está MÁS LEJOS que "uno" pero se inserta primero: si el ranking ordenara a
        // los llegados por distancia (en vez de preservar el orden de inserción) este test
        // fallaría, porque esperaríamos a "uno" primero.
        val fartherButInsertedFirst = peerAt("dos", 0.0002, 40.0) // ~22 m -> llegó
        val closerButInsertedSecond = peerAt("uno", 0.00005, 40.0) // ~5.6 m -> llegó
        val pending = peerAt("tres", 0.002, 40.0) // ~222 m -> pendiente

        val entries = RankingCalc.rank(
            self = null,
            selfSpeedKmh = null,
            peers = listOf(pending, fartherButInsertedFirst, closerButInsertedSecond),
            dest = dest,
        )

        assertEquals(listOf("dos", "uno", "tres"), entries.map { it.name })
        assertTrue(entries[0].arrived)
        assertTrue(entries[1].arrived)
        assertFalse(entries[2].arrived)
    }

    @Test
    fun `marca llegada bajo el radio configurado`() {
        val closePeer = peerAt("close", 0.0002, 10.0) // ~22 m
        val farPeer = peerAt("far", 0.001, 10.0) // ~111 m

        val entries = RankingCalc.rank(
            self = null,
            selfSpeedKmh = null,
            peers = listOf(closePeer, farPeer),
            dest = dest,
            arrivalRadiusM = 40.0,
        )

        assertTrue(entries.first { it.name == "close" }.arrived)
        assertFalse(entries.first { it.name == "far" }.arrived)
    }

    @Test
    fun `sin velocidad conocida el eta es null y va al fondo`() {
        val noSpeed = peerAt("mudo", 0.0005, null) // ~56 m, sin velocidad
        val withSpeed = peerAt("rapido", 0.002, 60.0) // ~222 m, con velocidad

        val entries = RankingCalc.rank(self = null, selfSpeedKmh = null, peers = listOf(noSpeed, withSpeed), dest = dest)

        assertEquals(listOf("rapido", "mudo"), entries.map { it.name })
        assertNull(entries.first { it.name == "mudo" }.etaMin)
    }

    @Test
    fun `calcula eta con piso de 5 kmh`() {
        val slow = peerAt("lento", 0.001, 1.0) // ~111 m a un piso de 5 km/h

        val entry = RankingCalc.rank(self = null, selfSpeedKmh = null, peers = listOf(slow), dest = dest).single()

        assertEquals(1.33, entry.etaMin!!, 0.05)
    }

    @Test
    fun `marca self correctamente y lo distingue de los peers`() {
        val selfPoint = "yo" to LiveRouteHolder.RoutePoint(lat = 0.0005, lon = 0.0)
        val peer = peerAt("otro", 0.001, 30.0)

        val entries = RankingCalc.rank(self = selfPoint, selfSpeedKmh = 20.0, peers = listOf(peer), dest = dest)

        assertTrue(entries.first { it.name == "yo" }.isSelf)
        assertFalse(entries.first { it.name == "otro" }.isSelf)
    }

    @Test
    fun `sin posicion propia no rompe el ranking`() {
        val peer = peerAt("solo", 0.001, 20.0)

        val entries = RankingCalc.rank(self = null, selfSpeedKmh = null, peers = listOf(peer), dest = dest)

        assertEquals(1, entries.size)
        assertEquals("solo", entries.single().name)
    }

    @Test
    fun `countdown oculto mas alla de 5 segundos antes de largar`() {
        assertNull(RaceCountdown.secondsToShow(startAtMs = 5_001, nowMs = 0))
    }

    @Test
    fun `countdown muestra 5 justo al entrar a la ventana`() {
        assertEquals(5, RaceCountdown.secondsToShow(startAtMs = 5_000, nowMs = 0))
    }

    @Test
    fun `countdown cuenta 5 4 3 2 1 por segundo`() {
        assertEquals(5, RaceCountdown.secondsToShow(startAtMs = 4_500, nowMs = 0))
        assertEquals(4, RaceCountdown.secondsToShow(startAtMs = 3_500, nowMs = 0))
        assertEquals(3, RaceCountdown.secondsToShow(startAtMs = 2_500, nowMs = 0))
        assertEquals(2, RaceCountdown.secondsToShow(startAtMs = 1_500, nowMs = 0))
        assertEquals(1, RaceCountdown.secondsToShow(startAtMs = 500, nowMs = 0))
    }

    @Test
    fun `countdown muestra YA representado como 0 justo al largar`() {
        assertEquals(0, RaceCountdown.secondsToShow(startAtMs = 0, nowMs = 0))
    }

    @Test
    fun `countdown sigue en 0 hasta justo antes de los 2 segundos post largada`() {
        assertEquals(0, RaceCountdown.secondsToShow(startAtMs = 0, nowMs = 1_999))
    }

    @Test
    fun `countdown se oculta a los 2 segundos post largada`() {
        assertNull(RaceCountdown.secondsToShow(startAtMs = 0, nowMs = 2_000))
    }
}
