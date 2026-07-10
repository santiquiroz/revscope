package com.revscope.core.obd.safety

import com.revscope.core.obd.safety.CrashDetector.State
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashDetectorTest {

    private val detector = CrashDetector()

    @Test
    fun `impacto seguido de 30s inmovil dispara TRIGGERED`() {
        var now = 0L
        assertEquals(State.MONITORING, detector.process(accelG = 1.0, speedKmh = 60.0, nowMs = now))

        // Impacto: pico > 6G con velocidad previa > 20 km/h dentro de los últimos 5s
        now += 100
        assertEquals(State.IMPACT_DETECTED, detector.process(accelG = 8.0, speedKmh = 5.0, nowMs = now))

        // Inmovilidad sostenida 30s: velocidad < 3 km/h y |a| < 1.3G
        repeat(30) {
            now += 1_000
            val state = detector.process(accelG = 0.5, speedKmh = 0.0, nowMs = now)
            if (it < 29) assertEquals(State.IMPACT_DETECTED, state)
        }
        assertEquals(State.TRIGGERED, detector.process(accelG = 0.5, speedKmh = 0.0, nowMs = now + 1_000))
    }

    @Test
    fun `impacto pero el conductor sigue rodando vuelve a MONITORING`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 60.0, nowMs = now)
        now += 100
        assertEquals(State.IMPACT_DETECTED, detector.process(accelG = 8.0, speedKmh = 30.0, nowMs = now))

        now += 1_000
        assertEquals(State.MONITORING, detector.process(accelG = 0.2, speedKmh = 25.0, nowMs = now))
    }

    @Test
    fun `bache de 5G a 60 kmh nunca dispara IMPACT_DETECTED`() {
        var now = 0L
        repeat(5) {
            now += 200
            assertEquals(State.MONITORING, detector.process(accelG = 5.0, speedKmh = 60.0, nowMs = now))
        }
    }

    @Test
    fun `caida desde quieto sin velocidad previa no dispara`() {
        var now = 0L
        assertEquals(State.MONITORING, detector.process(accelG = 0.0, speedKmh = 0.0, nowMs = now))

        now += 500
        // Golpe fuerte (p. ej. celular cayendo de la mano) pero sin haber ido a >20 km/h antes
        assertEquals(State.MONITORING, detector.process(accelG = 9.0, speedKmh = 0.0, nowMs = now))

        now += 1_000
        assertEquals(State.MONITORING, detector.process(accelG = 0.3, speedKmh = 0.0, nowMs = now))
    }

    @Test
    fun `impacto exactamente en 6G no cuenta como pico`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 60.0, nowMs = now)
        now += 100
        assertEquals(State.MONITORING, detector.process(accelG = 6.0, speedKmh = 30.0, nowMs = now))
    }

    @Test
    fun `velocidad previa exactamente en 20 kmh no cuenta como calificante`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 20.0, nowMs = now)
        now += 100
        assertEquals(State.MONITORING, detector.process(accelG = 8.0, speedKmh = 5.0, nowMs = now))
    }

    @Test
    fun `velocidad alta fuera de la ventana de 5s ya no califica el impacto`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 40.0, nowMs = now)

        now += 5_001
        detector.process(accelG = 1.0, speedKmh = 0.0, nowMs = now)

        now += 100
        assertEquals(State.MONITORING, detector.process(accelG = 8.0, speedKmh = 0.0, nowMs = now))
    }

    @Test
    fun `velocidad alta dentro de la ventana de 5s si califica el impacto`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 40.0, nowMs = now)

        now += 4_000
        detector.process(accelG = 1.0, speedKmh = 0.0, nowMs = now)

        now += 100
        assertEquals(State.IMPACT_DETECTED, detector.process(accelG = 8.0, speedKmh = 0.0, nowMs = now))
    }

    @Test
    fun `interrupcion de la inmovilidad antes de 30s reinicia la cuenta`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 60.0, nowMs = now)
        now += 100
        detector.process(accelG = 8.0, speedKmh = 5.0, nowMs = now)

        // 20s inmóvil...
        repeat(20) {
            now += 1_000
            detector.process(accelG = 0.5, speedKmh = 0.0, nowMs = now)
        }
        // ...se mueve un poco (rango intermedio 3-10 km/h) y rompe la inmovilidad sostenida
        now += 1_000
        assertEquals(State.IMPACT_DETECTED, detector.process(accelG = 0.5, speedKmh = 5.0, nowMs = now))

        // otros 29s inmóvil NO deberían disparar — el conteo se reinició
        repeat(29) {
            now += 1_000
            assertEquals(State.IMPACT_DETECTED, detector.process(accelG = 0.5, speedKmh = 0.0, nowMs = now))
        }
        // aún no se cumplen los 30s desde que la inmovilidad se reinició (29s exactos)
        now += 1_000
        assertEquals(State.IMPACT_DETECTED, detector.process(accelG = 0.5, speedKmh = 0.0, nowMs = now))

        // se completan los 30s desde que la inmovilidad se reinició
        now += 1_000
        assertEquals(State.TRIGGERED, detector.process(accelG = 0.5, speedKmh = 0.0, nowMs = now))
    }

    @Test
    fun `movimiento mayor a 10 kmh tras el impacto vuelve a MONITORING de inmediato`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 60.0, nowMs = now)
        now += 100
        detector.process(accelG = 8.0, speedKmh = 5.0, nowMs = now)

        now += 5_000
        detector.process(accelG = 0.5, speedKmh = 0.0, nowMs = now) // empieza inmovilidad

        now += 1_000
        assertEquals(State.MONITORING, detector.process(accelG = 0.5, speedKmh = 15.0, nowMs = now))
    }

    @Test
    fun `hadRecentMotion es true si hubo velocidad mayor a 20 kmh dentro de la ventana`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 45.0, nowMs = now)
        now += 10_000
        assertEquals(true, detector.hadRecentMotion(windowMs = 60_000L, nowMs = now))
    }

    @Test
    fun `hadRecentMotion es false si solo hubo velocidad lenta dentro de la ventana`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 15.0, nowMs = now)
        now += 10_000
        assertEquals(false, detector.hadRecentMotion(windowMs = 60_000L, nowMs = now))
    }

    @Test
    fun `hadRecentMotion es false sin historial de velocidad`() {
        assertEquals(false, detector.hadRecentMotion(windowMs = 60_000L, nowMs = 0L))
    }

    @Test
    fun `hadRecentMotion recuerda velocidad rapida de hace casi 60s`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 45.0, nowMs = now)
        now += 59_000
        detector.process(accelG = 0.0, speedKmh = 0.0, nowMs = now)
        assertEquals(true, detector.hadRecentMotion(windowMs = 60_000L, nowMs = now))
    }

    @Test
    fun `reset vuelve a MONITORING y olvida el historial de velocidad`() {
        var now = 0L
        detector.process(accelG = 1.0, speedKmh = 60.0, nowMs = now)
        now += 100
        assertEquals(State.IMPACT_DETECTED, detector.process(accelG = 8.0, speedKmh = 5.0, nowMs = now))

        detector.reset()
        now += 100
        assertEquals(State.MONITORING, detector.process(accelG = 8.0, speedKmh = 0.0, nowMs = now))
    }
}
