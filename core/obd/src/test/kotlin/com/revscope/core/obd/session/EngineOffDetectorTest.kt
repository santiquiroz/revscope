package com.revscope.core.obd.session

import org.junit.Assert.assertFalse
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
}
