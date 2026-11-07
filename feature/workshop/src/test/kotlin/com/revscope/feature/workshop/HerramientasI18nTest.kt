package com.revscope.feature.workshop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class HerramientasI18nTest {
    private val resolverEn = XmlStringResolver.en(R::class.java)

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `al dia renders its empty state in english`() {
        compose.setContent {
            RevScopeTheme {
                AlDiaContent(
                    vehiculo = null,
                    estados = emptyList(),
                    licenciaVenceEn = null,
                    mantenimientoNivel = DocumentStatusCalculator.Nivel.OK,
                    mantenimientoDetalle = "",
                    zoneBrief = null,
                    acciones = AccionesAlDia(),
                )
            }
        }

        compose.onNodeWithText(resolverEn.get(R.string.workshop_al_dia_no_vehicle)).assertIsDisplayed()
    }

    @Test
    fun `odometer explanation has its english translation`() {
        assertEquals(
            "Your vehicle does not expose standard PID 01 A6 — common on motorcycles and vehicles made before 2015. " +
                "Use the Mode 22 scanner (Workshop → Advanced scanner) to find the manufacturer-specific DID.",
            resolverEn.get(R.string.workshop_odometer_unavailable_description),
        )
    }
}
