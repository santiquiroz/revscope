package com.revscope.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnidadesSectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `clicking US when tagActual is automatic triggers onElegir with US`() {
        var invocations = 0
        var selected: String? = null

        compose.setContent {
            UnidadesSection(tagActual = "") {
                invocations += 1
                selected = it
            }
        }

        compose.onNodeWithText("US (mph, mi, °F, psi)").performClick()

        assertEquals(1, invocations)
        assertEquals("US", selected)
    }

    @Test
    fun `clicking already selected option does not trigger onElegir`() {
        var invocations = 0

        compose.setContent {
            UnidadesSection(tagActual = "METRIC") {
                invocations += 1
            }
        }

        compose.onNodeWithText("Metric (km/h, km, °C, kPa)").performClick()

        assertEquals(0, invocations)
    }
}
