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
class IdiomaSectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `clicking Espanol when tagActual is system triggers onElegir once`() {
        var invocations = 0
        var selected: String? = null

        compose.setContent {
            IdiomaSection(tagActual = "") {
                invocations += 1
                selected = it
            }
        }

        compose.onNodeWithText("Español").performClick()

        assertEquals(1, invocations)
        assertEquals("es", selected)
    }

    @Test
    fun `clicking already selected English does not trigger onElegir`() {
        var invocations = 0

        compose.setContent {
            IdiomaSection(tagActual = "en") {
                invocations += 1
            }
        }

        compose.onNodeWithText("English").performClick()

        assertEquals(0, invocations)
    }
}
