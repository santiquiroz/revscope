package com.revscope.core.obd.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOffDetectorTest {

    private var now = 0L
    private val detector = EngineOffDetector(clock = { now })

    @Test
    fun `sin movimiento nunca reporta movimiento reciente`() {
        assertFalse(detector.movedRecently())
    }

    @Test
    fun `movimiento dentro de la ventana cuenta como reciente`() {
        detector.onSpeed(45.0)
        now += 29_000
        assertTrue(detector.movedRecently())
    }

    @Test
    fun `movimiento fuera de la ventana ya no es reciente`() {
        detector.onSpeed(45.0)
        now += 31_000
        assertFalse(detector.movedRecently())
    }

    @Test
    fun `velocidad bajo el umbral es ruido y no cuenta`() {
        detector.onSpeed(2.0)
        assertFalse(detector.movedRecently())
    }

    @Test
    fun `reset olvida el movimiento`() {
        detector.onSpeed(45.0)
        detector.reset()
        assertFalse(detector.movedRecently())
    }

    // ── movedRecently(windowMs) — used by the GPS-trip inactivity watcher ──────

    @Test
    fun `sin movimiento lastMovementTimestamp es null`() {
        assertNull(detector.lastMovementTimestamp())
    }

    @Test
    fun `lastMovementTimestamp devuelve el instante del ultimo movimiento`() {
        detector.onSpeed(45.0)
        assertEquals(0L, detector.lastMovementTimestamp())
        now += 10_000
        detector.onSpeed(45.0)
        assertEquals(10_000L, detector.lastMovementTimestamp())
    }

    @Test
    fun `movedRecently con ventana larga cuenta movimiento fuera de la ventana corta`() {
        detector.onSpeed(45.0)
        now += 31_000 // fuera de la ventana por defecto (30 s)
        assertFalse(detector.movedRecently())
        assertTrue(detector.movedRecently(windowMs = 4 * 60_000L))
    }

    @Test
    fun `movedRecently con ventana larga vence pasado el umbral`() {
        detector.onSpeed(45.0)
        now += 4 * 60_000L + 1
        assertFalse(detector.movedRecently(windowMs = 4 * 60_000L))
    }
}
