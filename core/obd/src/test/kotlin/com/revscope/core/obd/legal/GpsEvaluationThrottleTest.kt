package com.revscope.core.obd.legal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsEvaluationThrottleTest {

    @Test
    fun `primera evaluacion sin registro previo siempre se permite`() {
        val throttle = GpsEvaluationThrottle()

        val result = throttle.shouldEvaluate(BASE_LAT, BASE_LON, NOW_MS)

        assertTrue(result)
    }

    @Test
    fun `dentro de la ventana de 120s no se evalua aunque el fix este lejos`() {
        val throttle = GpsEvaluationThrottle()
        throttle.recordEvaluation(BASE_LAT, BASE_LON, NOW_MS)

        val result = throttle.shouldEvaluate(FAR_LAT, BASE_LON, NOW_MS + WITHIN_THROTTLE_MS)

        assertFalse(result)
    }

    @Test
    fun `pasada la ventana de 120s pero cerca del ultimo evaluado no se evalua`() {
        val throttle = GpsEvaluationThrottle()
        throttle.recordEvaluation(BASE_LAT, BASE_LON, NOW_MS)

        val result = throttle.shouldEvaluate(CLOSE_LAT, BASE_LON, NOW_MS + AFTER_THROTTLE_MS)

        assertFalse(result)
    }

    @Test
    fun `pasada la ventana de 120s y movido mas de 3km si se evalua`() {
        val throttle = GpsEvaluationThrottle()
        throttle.recordEvaluation(BASE_LAT, BASE_LON, NOW_MS)

        val result = throttle.shouldEvaluate(FAR_LAT, BASE_LON, NOW_MS + AFTER_THROTTLE_MS)

        assertTrue(result)
    }

    @Test
    fun `recordEvaluation reinicia el punto de referencia del throttle`() {
        val throttle = GpsEvaluationThrottle()
        throttle.recordEvaluation(BASE_LAT, BASE_LON, NOW_MS)
        throttle.recordEvaluation(FAR_LAT, BASE_LON, NOW_MS + AFTER_THROTTLE_MS)

        // Mismo punto que el último registrado (FAR_LAT), ventana ya pasada de nuevo
        val result = throttle.shouldEvaluate(FAR_LAT, BASE_LON, NOW_MS + AFTER_THROTTLE_MS + AFTER_THROTTLE_MS)

        assertFalse(result)
    }

    private companion object {
        const val BASE_LAT = 6.2442
        const val BASE_LON = -75.5812
        const val CLOSE_LAT = 6.2492 // ~0.56km north — inside the 3km floor
        const val FAR_LAT = 6.2942 // ~5.56km north — outside the 3km floor
        const val WITHIN_THROTTLE_MS = 60_000L
        const val AFTER_THROTTLE_MS = 130_000L
        const val NOW_MS = 1_780_326_000_000L
    }
}
