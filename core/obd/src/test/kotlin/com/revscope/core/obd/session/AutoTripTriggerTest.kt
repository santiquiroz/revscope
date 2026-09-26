package com.revscope.core.obd.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTripTriggerTest {

    private fun disparos(trigger: AutoTripTrigger, muestras: List<Pair<Long, Double>>): List<Long> =
        muestras.filter { (t, kmh) -> trigger.onVelocidad(kmh, t) }.map { it.first }

    @Test
    fun `10 kmh sostenidos 5 s disparan una vez`() {
        val muestras = (0L..8_000L step 500L).map { it to 12.0 }

        assertEquals(listOf(5_000L), disparos(AutoTripTrigger(), muestras))
    }

    @Test
    fun `un pico de 3 s no dispara`() {
        val muestras = (0L..3_000L step 500L).map { it to 15.0 } + (3_500L to 0.0) + (9_000L to 0.0)

        assertTrue(disparos(AutoTripTrigger(), muestras).isEmpty())
    }

    @Test
    fun `bajar del umbral reinicia la cuenta`() {
        val trigger = AutoTripTrigger()
        trigger.onVelocidad(20.0, 0L)
        trigger.onVelocidad(5.0, 4_000L)

        assertFalse(trigger.onVelocidad(20.0, 6_000L))
        assertTrue(trigger.onVelocidad(20.0, 11_000L))
    }

    @Test
    fun `tras disparar exige volver a bajar del umbral`() {
        val trigger = AutoTripTrigger()
        trigger.onVelocidad(20.0, 0L)
        trigger.onVelocidad(20.0, 5_000L)

        assertFalse(trigger.onVelocidad(20.0, 20_000L))
        trigger.onVelocidad(0.0, 21_000L)
        trigger.onVelocidad(20.0, 22_000L)
        assertTrue(trigger.onVelocidad(20.0, 27_000L))
    }

    @Test
    fun `reset olvida la racha`() {
        val trigger = AutoTripTrigger()
        trigger.onVelocidad(20.0, 0L)
        trigger.reset()

        assertFalse(trigger.onVelocidad(20.0, 5_000L))
    }

    @Test
    fun `finalizar en movimiento exige parar antes de rearmar`() {
        val trigger = AutoTripTrigger()
        trigger.exigirParada()

        assertFalse(trigger.onVelocidad(60.0, 0L))
        assertFalse(trigger.onVelocidad(60.0, 30_000L))
        trigger.onVelocidad(0.0, 31_000L)
        trigger.onVelocidad(30.0, 32_000L)
        assertTrue(trigger.onVelocidad(30.0, 37_000L))
    }
}
