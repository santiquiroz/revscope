package com.revscope.feature.workshop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revscope.core.designsystem.DESCRIPCION_VOLVER
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.taller.pid.DisponibilidadPid
import com.revscope.core.obd.workshop.DiagnosticRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticaTallerTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `cada nivel de documento tiene texto propio ademas del color`() {
        assertEquals(NivelConTexto(NivelEstado.FALLA, "Vencido"), nivelDocumento(DocumentStatusCalculator.Nivel.VENCIDO))
        assertEquals("Sin configurar", nivelDocumento(DocumentStatusCalculator.Nivel.SIN_CONFIGURAR).texto)
        assertEquals(NivelEstado.ATENCION, nivelDiagnostico(DiagnosticRules.Nivel.ATENCION))
    }

    @Test
    fun `el chequeo expone el nivel como texto`() {
        val falla = DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.FALLA, "Códigos", "P0122", "Revisa el TPS")
        compose.setContent {
            RevScopeTheme {
                HealthCheckContent(HealthCheckViewModel.UiState.Done(listOf(falla), listOf("P0122"), 0L), AccionesChequeo())
            }
        }

        compose.onNodeWithText("Falla").assertIsDisplayed()
    }

    @Test
    fun `el chequeo muestra parametros no disponibles como sin dato`() {
        compose.setContent {
            RevScopeTheme {
                HealthCheckContent(
                    HealthCheckViewModel.UiState.Done(
                        items = emptyList(),
                        dtcCodes = emptyList(),
                        timestamp = 0L,
                        noDisponibles = listOf("Sensor O2 B1S1"),
                    ),
                    AccionesChequeo(),
                )
            }
        }

        compose.onNodeWithText("Sensor O2 B1S1").assertIsDisplayed()
        compose.onNodeWithText("Sin dato").assertIsDisplayed()
        compose.onNodeWithText("No disponible en esta ECU").assertIsDisplayed()
    }

    @Test
    fun `mezcla conserva las filas no soportadas y explica el motivo`() {
        val registry = PidRegistry("[]").apply { setSupportedPids(emptySet()) }
        compose.setContent {
            RevScopeTheme {
                LiveMixtureContent(
                    readings = emptyMap(),
                    definition = registry::getDefinition,
                    disponibilidad = { pid, nombre -> DisponibilidadPid.resolver(pid, nombre, registry) },
                )
            }
        }

        compose.onNodeWithText("Fuel trim corto B1").assertIsDisplayed()
        compose.onAllNodesWithText("No disponible en esta ECU")[0].assertIsDisplayed()
        compose.onNodeWithText("Esta ECU no reporta fuel trim corto b1 (PID 06).").assertIsDisplayed()
    }

    @Test
    fun `el error del chequeo reintenta el escaneo`() {
        var escaneos = 0
        compose.setContent {
            RevScopeTheme {
                HealthCheckContent(
                    HealthCheckViewModel.UiState.Error("Conecta el adaptador primero"),
                    AccionesChequeo(onEscanear = { escaneos++ }),
                )
            }
        }

        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(1, escaneos)
    }

    @Test
    fun `al dia tiene Volver`() {
        var volvio = false
        compose.setContent {
            RevScopeTheme {
                AlDiaContent(
                    vehiculo = null,
                    estados = emptyList(),
                    licenciaVenceEn = null,
                    mantenimientoNivel = DocumentStatusCalculator.Nivel.OK,
                    mantenimientoDetalle = "",
                    zoneBrief = null,
                    acciones = AccionesAlDia(onVolver = { volvio = true }),
                )
            }
        }

        compose.onNodeWithText("Ir a perfiles").assertIsDisplayed()
        compose.onNodeWithContentDescription(DESCRIPCION_VOLVER).performClick()

        assertTrue(volvio)
    }
}
