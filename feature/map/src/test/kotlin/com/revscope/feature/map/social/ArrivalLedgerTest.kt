package com.revscope.feature.map.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalLedgerTest {

    private fun entry(name: String, arrived: Boolean, remainingM: Double = 0.0, isSelf: Boolean = false) =
        RankingCalc.Entry(name = name, remainingM = remainingM, etaMin = null, arrived = arrived, isSelf = isSelf)

    @Test
    fun `orden real de cruce en updates sucesivos, no el de insercion en la lista`() {
        val startAtMs = 10_000L
        var state = ArrivalLedger.step(
            ArrivalLedger.State(),
            listOf(entry("b", arrived = false), entry("a", arrived = false)),
            startAtMs,
            nowMs = 0,
        )

        // "a" cruza primero, aunque en la lista de este tick "b" aparece antes.
        state = ArrivalLedger.step(state, listOf(entry("b", false), entry("a", true)), startAtMs, nowMs = startAtMs + 100)
        assertEquals(1, state.finishers.getValue("a").order)
        assertTrue("b" !in state.finishers)

        // "b" cruza en un tick posterior.
        state = ArrivalLedger.step(state, listOf(entry("b", true), entry("a", true)), startAtMs, nowMs = startAtMs + 500)
        assertEquals(1, state.finishers.getValue("a").order)
        assertEquals(2, state.finishers.getValue("b").order)
    }

    @Test
    fun `mi cruce con otros dos ya llegados me acredita el tercer puesto`() {
        val startAtMs = 10_000L
        var state = ArrivalLedger.step(
            ArrivalLedger.State(),
            listOf(entry("uno", false), entry("dos", false), entry("yo", false, isSelf = true)),
            startAtMs,
            nowMs = 0,
        )
        state = ArrivalLedger.step(state, listOf(entry("uno", true), entry("dos", false), entry("yo", false, isSelf = true)), startAtMs, nowMs = startAtMs + 100)
        state = ArrivalLedger.step(state, listOf(entry("uno", true), entry("dos", true), entry("yo", false, isSelf = true)), startAtMs, nowMs = startAtMs + 200)

        state = ArrivalLedger.step(state, listOf(entry("uno", true), entry("dos", true), entry("yo", true, isSelf = true)), startAtMs, nowMs = startAtMs + 300)

        assertEquals(3, state.finishers.getValue("yo").order)
    }

    @Test
    fun `un finisher es sticky, se mantiene aunque desaparezca de las entries`() {
        val startAtMs = 10_000L
        var state = ArrivalLedger.step(
            ArrivalLedger.State(),
            listOf(entry("peer", false), entry("yo", false, isSelf = true)),
            startAtMs,
            nowMs = 0,
        )
        state = ArrivalLedger.step(state, listOf(entry("peer", true), entry("yo", false, isSelf = true)), startAtMs, nowMs = startAtMs + 100)
        assertEquals(1, state.finishers.getValue("peer").order)

        // "peer" ya no viene en las entries (p. ej. pruneado por stale, F5) — sigue en el ledger.
        state = ArrivalLedger.step(state, listOf(entry("yo", true, isSelf = true)), startAtMs, nowMs = startAtMs + 200)

        assertEquals("un finisher sticky no se pierde cuando su Peer deja de emitir", 1, state.finishers.getValue("peer").order)
        assertEquals(2, state.finishers.getValue("yo").order)
    }

    @Test
    fun `una carrera nueva descarta los finishers de la anterior`() {
        val firstRace = 10_000L
        val secondRace = 50_000L
        var state = ArrivalLedger.step(ArrivalLedger.State(), listOf(entry("a", false)), firstRace, nowMs = 0)
        state = ArrivalLedger.step(state, listOf(entry("a", true)), firstRace, nowMs = firstRace + 100)
        assertEquals(1, state.finishers.getValue("a").order)

        state = ArrivalLedger.step(state, listOf(entry("a", true)), secondRace, nowMs = secondRace)

        assertTrue("la carrera nueva no debe heredar finishers de la anterior", state.finishers.isEmpty())
        assertEquals(secondRace, state.raceKey)
    }

    @Test
    fun `sin carrera activa resetea todo el registro`() {
        val seeded = ArrivalLedger.State(raceKey = 1L, finishers = mapOf("a" to ArrivalLedger.Finisher(1, entry("a", true))))

        val state = ArrivalLedger.step(seeded, entries = emptyList(), raceKey = null, nowMs = 0)

        assertEquals(ArrivalLedger.State(), state)
    }

    @Test
    fun `cruce antes de la largada no asigna orden y no se reactiva despues`() {
        val startAtMs = 10_000L
        var state = ArrivalLedger.step(ArrivalLedger.State(), listOf(entry("a", false)), startAtMs, nowMs = 0)

        // Cruza ANTES de la largada.
        state = ArrivalLedger.step(state, listOf(entry("a", true)), startAtMs, nowMs = startAtMs - 500)
        assertTrue("a" !in state.finishers)

        // Sigue arrived=true tras la largada, pero ya no es un cruce nuevo (ya estaba adentro).
        state = ArrivalLedger.step(state, listOf(entry("a", true)), startAtMs, nowMs = startAtMs + 500)
        assertTrue(
            "un rider ya adentro del radio al largar no genera un cruce nuevo post-largada",
            "a" !in state.finishers,
        )
    }

    @Test
    fun `empates del mismo tick se desempatan por restante ascendente`() {
        val startAtMs = 10_000L
        var state = ArrivalLedger.step(
            ArrivalLedger.State(),
            listOf(entry("zulu", false, remainingM = 10.0), entry("alfa", false, remainingM = 30.0)),
            startAtMs,
            nowMs = 0,
        )

        state = ArrivalLedger.step(
            state,
            listOf(entry("zulu", true, remainingM = 10.0), entry("alfa", true, remainingM = 30.0)),
            startAtMs,
            nowMs = startAtMs + 100,
        )

        assertEquals(1, state.finishers.getValue("zulu").order)
        assertEquals(2, state.finishers.getValue("alfa").order)
    }

    @Test
    fun `empate exacto de restante se desempata por nombre`() {
        val startAtMs = 10_000L
        var state = ArrivalLedger.step(
            ArrivalLedger.State(),
            listOf(entry("bravo", false, remainingM = 5.0), entry("alfa", false, remainingM = 5.0)),
            startAtMs,
            nowMs = 0,
        )

        state = ArrivalLedger.step(
            state,
            listOf(entry("bravo", true, remainingM = 5.0), entry("alfa", true, remainingM = 5.0)),
            startAtMs,
            nowMs = startAtMs + 100,
        )

        assertEquals(1, state.finishers.getValue("alfa").order)
        assertEquals(2, state.finishers.getValue("bravo").order)
    }
}
