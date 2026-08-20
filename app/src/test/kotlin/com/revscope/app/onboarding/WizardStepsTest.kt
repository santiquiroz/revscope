package com.revscope.app.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class WizardStepsTest {
    @Test
    fun `next avanza y se detiene en el ultimo`() {
        assertEquals(1, WizardSteps.next(0))
        assertEquals(4, WizardSteps.next(4))
    }

    @Test
    fun `back retrocede y se detiene en cero`() {
        assertEquals(0, WizardSteps.back(0))
        assertEquals(3, WizardSteps.back(4))
    }

    @Test
    fun `clamp acota ambos extremos`() {
        assertEquals(0, WizardSteps.clamp(-3))
        assertEquals(4, WizardSteps.clamp(99))
    }
}
