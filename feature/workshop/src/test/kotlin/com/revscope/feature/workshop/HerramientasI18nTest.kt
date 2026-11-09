package com.revscope.feature.workshop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.revscope.core.common.i18n.Region
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.legal.DocumentStatusCalculator.DocType
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
                    region = Region.CO,
                    acciones = AccionesAlDia(),
                )
            }
        }

        compose.onNodeWithText(resolverEn.get(R.string.workshop_al_dia_no_vehicle)).assertIsDisplayed()
    }

    @Test
    fun `colombia in english shows SOAT and the SIMIT button`() {
        alDiaCon(Region.CO)

        compose.onNodeWithText("Mandatory insurance (SOAT)").assertExists()
        compose.onNodeWithText("Roadworthiness inspection (RTM)").assertExists()
        compose.onNodeWithText("Check SIMIT").assertExists()
    }

    @Test
    fun `united kingdom in english shows MOT and hides SIMIT`() {
        alDiaCon(Region.GB)

        compose.onNodeWithText("Insurance").assertExists()
        compose.onNodeWithText("MOT").assertExists()
        compose.onNodeWithText("Check SIMIT").assertDoesNotExist()
        compose.onNodeWithText("Mandatory insurance (SOAT)").assertDoesNotExist()
    }

    @Test
    fun `united states shows safety inspection without SIMIT`() {
        alDiaCon(Region.US)

        compose.onNodeWithText("Safety inspection").assertExists()
        compose.onNodeWithText("Check SIMIT").assertDoesNotExist()
    }

    private fun alDiaCon(region: Region) {
        compose.setContent {
            RevScopeTheme {
                Box(Modifier.requiredSize(412.dp, 2400.dp)) {
                    AlDiaContent(
                        vehiculo = VehiculoAlDia(nombre = "Car", placa = "ABC123"),
                        estados = estadosMinimos,
                        licenciaVenceEn = null,
                        mantenimientoNivel = DocumentStatusCalculator.Nivel.OK,
                        mantenimientoDetalle = "",
                        zoneBrief = null,
                        region = region,
                        acciones = AccionesAlDia(),
                    )
                }
            }
        }
    }

    private val estadosMinimos = listOf(
        DocumentStatusCalculator.DocStatus(DocType.SOAT, DocumentStatusCalculator.Nivel.OK, "x", "Expires in 100 days"),
        DocumentStatusCalculator.DocStatus(DocType.RTM, DocumentStatusCalculator.Nivel.OK, "y", "Expires in 200 days"),
    )

    @Test
    fun `odometer explanation has its english translation`() {
        assertEquals(
            "Your vehicle does not expose standard PID 01 A6 — common on motorcycles and vehicles made before 2015. " +
                "Use the Mode 22 scanner (Workshop → Advanced scanner) to find the manufacturer-specific DID.",
            resolverEn.get(R.string.workshop_odometer_unavailable_description),
        )
    }
}
