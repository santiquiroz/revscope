package com.revscope.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepCursorTest {

    private fun steps(vararg types: String) = types.map {
        RouteStep(Maneuver(it, null, null), LatLon(0.0, 0.0), 100.0, 30.0)
    }

    @Test
    fun `al arrancar faltan todos los pasos y se va por el primero`() {
        assertEquals(0, StepCursor.currentStepIndex(totalSteps = 15, remainingSteps = 15))
    }

    @Test
    fun `mientras se circula el paso cero lo que se aproxima es la maniobra del paso uno`() {
        assertEquals(1, StepCursor.nextManeuverIndex(totalSteps = 15, remainingSteps = 15))
    }

    @Test
    fun `a mitad de ruta el indice acompana el avance`() {
        assertEquals(5, StepCursor.currentStepIndex(totalSteps = 15, remainingSteps = 10))
        assertEquals(6, StepCursor.nextManeuverIndex(totalSteps = 15, remainingSteps = 10))
    }

    @Test
    fun `en el ultimo paso la maniobra que se aproxima es la llegada y no se sale del rango`() {
        assertEquals(14, StepCursor.currentStepIndex(totalSteps = 15, remainingSteps = 1))
        assertEquals(14, StepCursor.nextManeuverIndex(totalSteps = 15, remainingSteps = 1))
    }

    @Test
    fun `cero pasos restantes no desborda`() {
        assertEquals(14, StepCursor.currentStepIndex(totalSteps = 15, remainingSteps = 0))
        assertEquals(14, StepCursor.nextManeuverIndex(totalSteps = 15, remainingSteps = 0))
    }

    @Test
    fun `mas pasos restantes que totales no produce indice negativo`() {
        assertEquals(0, StepCursor.currentStepIndex(totalSteps = 3, remainingSteps = 9))
        assertEquals(1, StepCursor.nextManeuverIndex(totalSteps = 3, remainingSteps = 9))
    }

    @Test
    fun `una ruta sin pasos no tiene maniobra`() {
        assertNull(StepCursor.maneuverAhead(emptyList(), remainingSteps = 0))
    }

    @Test
    fun `una ruta de un solo paso se queda en ese paso`() {
        assertEquals(0, StepCursor.nextManeuverIndex(totalSteps = 1, remainingSteps = 1))
    }

    @Test
    fun `la maniobra que se anuncia es la del paso siguiente`() {
        val ruta = steps("depart", "turn", "arrive")

        assertEquals("turn", StepCursor.maneuverAhead(ruta, remainingSteps = 3)?.type)
        assertEquals("arrive", StepCursor.maneuverAhead(ruta, remainingSteps = 2)?.type)
        assertEquals("arrive", StepCursor.maneuverAhead(ruta, remainingSteps = 1)?.type)
    }
}
