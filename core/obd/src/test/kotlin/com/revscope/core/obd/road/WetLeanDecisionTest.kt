package com.revscope.core.obd.road

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WetLeanDecisionTest {

    // Curva real bajo lluvia: rápido, muy inclinado, con G lateral, calibrado
    private fun realCorner() = WetLeanDecision.qualifies(
        rainActive = true, calibrated = true, speedKmh = 60f,
        leanDeg = 35f, lateralG = 0.4f, dryMaxLeanDeg = 40f,
    )

    @Test
    fun `curva real bajo lluvia califica`() {
        assertTrue(realCorner())
    }

    @Test
    fun `sin lluvia nunca califica`() {
        assertFalse(
            WetLeanDecision.qualifies(false, true, 60f, 35f, 0.4f, 40f),
        )
    }

    @Test
    fun `telefono en bolsillo caminando no califica — sin velocidad`() {
        // Lean alto por movimiento de pierna pero a 4 km/h y sin G lateral
        assertFalse(
            WetLeanDecision.qualifies(true, true, 4f, 45f, 0.05f, 0f),
        )
    }

    @Test
    fun `bolsillo a velocidad pero sin G lateral no califica`() {
        // Va en la moto con el celular en el bolsillo: lean espurio, pero recto → sin G lateral
        assertFalse(
            WetLeanDecision.qualifies(true, true, 60f, 40f, 0.08f, 0f),
        )
    }

    @Test
    fun `sin calibrar no califica aunque el lean sea alto`() {
        assertFalse(
            WetLeanDecision.qualifies(true, false, 60f, 40f, 0.4f, 40f),
        )
    }

    @Test
    fun `lean bajo el umbral no califica`() {
        assertFalse(
            WetLeanDecision.qualifies(true, true, 60f, 25f, 0.4f, 0f),
        )
    }

    @Test
    fun `umbral es 30 grados sin historial y 75 por ciento con historial`() {
        assertEquals(30f, WetLeanDecision.threshold(0f), 0.01f)
        assertEquals(30f, WetLeanDecision.threshold(30f), 0.01f) // 22.5 < 30 → piso
        assertEquals(45f, WetLeanDecision.threshold(60f), 0.01f) // 75% de 60
    }
}
