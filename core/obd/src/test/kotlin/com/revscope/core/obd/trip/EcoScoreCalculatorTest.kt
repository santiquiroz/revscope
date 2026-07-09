package com.revscope.core.obd.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class EcoScoreCalculatorTest {

    private val redline = 6_500

    @Test
    fun `listas vacias dan score 100 sin bonus ni eventos`() {
        val d = EcoScoreCalculator.calculate(emptyList(), emptyList(), redline)
        assertEquals(100, d.score)
        assertEquals(0, d.aceleradasBruscas)
        assertEquals(0, d.frenadasBruscas)
        assertEquals(0, d.tiempoAltasRpmSeg)
        assertEquals(0, d.bonusCrucero)
    }

    @Test
    fun `viaje suave en crucero da 100 con bonus`() {
        val accel = List(60) { 0.0 }
        // 2500 rpm constante ~38% del redline (6500) — dentro de la banda 20-60%, estable
        val rpm = (0..59).map { s -> s * 1_000L to 2_500.0 }
        val d = EcoScoreCalculator.calculate(accel, rpm, redline)
        assertEquals(100, d.score)
        assertEquals(10, d.bonusCrucero)
        assertEquals(0, d.aceleradasBruscas)
        assertEquals(0, d.frenadasBruscas)
        assertEquals(0, d.tiempoAltasRpmSeg)
    }

    @Test
    fun `cuatro frenadas bruscas restan doce puntos`() {
        val accel = listOf(0.0, -5.0, 0.0, -5.0, 0.0, -5.0, 0.0, -5.0, 0.0)
        val d = EcoScoreCalculator.calculate(accel, emptyList(), redline)
        assertEquals(4, d.frenadasBruscas)
        assertEquals(0, d.aceleradasBruscas)
        assertEquals(88, d.score)
    }

    @Test
    fun `aceleradas bruscas restan dos puntos cada una`() {
        val accel = listOf(0.0, 4.0, 0.0, 4.0, 0.0)
        val d = EcoScoreCalculator.calculate(accel, emptyList(), redline)
        assertEquals(2, d.aceleradasBruscas)
        assertEquals(96, d.score)
    }

    @Test
    fun `rpm alto sostenido resta un punto cada 30 segundos`() {
        // 6000 rpm (92% del redline, > 80%) constante durante 90 s
        val rpm = listOf(0L to 6_000.0, 90_000L to 6_000.0)
        val d = EcoScoreCalculator.calculate(emptyList(), rpm, redline)
        assertEquals(90, d.tiempoAltasRpmSeg)
        assertEquals(0, d.bonusCrucero)
        assertEquals(97, d.score)
    }

    @Test
    fun `no cruza el umbral si nunca supera el limite`() {
        val accel = listOf(0.0, 1.0, 2.0, 2.9, 1.0, 0.0)
        val d = EcoScoreCalculator.calculate(accel, emptyList(), redline)
        assertEquals(0, d.aceleradasBruscas)
        assertEquals(100, d.score)
    }

    @Test
    fun `el score nunca baja de cero`() {
        val accel = (0 until 40).flatMap { listOf(0.0, -5.0) }
        val d = EcoScoreCalculator.calculate(accel, emptyList(), redline)
        assertEquals(0, d.score)
    }

    @Test
    fun `el bonus de crucero no supera diez`() {
        val accel = emptyList<Double>()
        val rpm = (0..119).map { s -> s * 1_000L to 3_000.0 }
        val d = EcoScoreCalculator.calculate(accel, rpm, redline)
        assertEquals(10, d.bonusCrucero)
    }
}
