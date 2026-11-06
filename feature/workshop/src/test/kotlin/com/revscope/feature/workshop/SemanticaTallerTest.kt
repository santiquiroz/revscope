package com.revscope.feature.workshop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revscope.core.designsystem.R as DesignSystemR
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.uitesting.XmlStringResolver
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.R as ObdR
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
@Config(sdk = [34], qualifiers = "es")
class SemanticaTallerTest {
    private val resolverEs = XmlStringResolver.es(
        R::class.java,
        ObdR::class.java,
        DesignSystemR::class.java,
        resDirs = listOf("src/main/res", "../../core/obd/src/main/res", "../../core/designsystem/src/main/res"),
    )

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `cada nivel de documento tiene texto propio ademas del color`() {
        val vencido = nivelDocumento(DocumentStatusCalculator.Nivel.VENCIDO)
        assertEquals(NivelEstado.FALLA, vencido.nivel)
        assertEquals("Vencido", resolverEs.get(vencido.textoRes))
        val sinConfigurar = nivelDocumento(DocumentStatusCalculator.Nivel.SIN_CONFIGURAR)
        assertEquals("Sin configurar", resolverEs.get(sinConfigurar.textoRes))
        assertEquals(NivelEstado.ATENCION, nivelDiagnostico(DiagnosticRules.Nivel.ATENCION))
    }

    @Test
    @Config(sdk = [34], qualifiers = "en")
    fun `el chequeo muestra su introduccion en ingles`() {
        compose.setContent {
            RevScopeTheme {
                HealthCheckContent(HealthCheckViewModel.UiState.Idle, AccionesChequeo())
            }
        }

        compose.onNodeWithText(
            "One tap and RevScope checks trouble codes, readiness monitors for your safety inspection, fuel mixture, O2 sensor, battery, and temperature.",
        ).assertIsDisplayed()
    }

    @Test
    fun `el chequeo expone el nivel como texto`() {
        val falla = DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.FALLA, "Códigos", "P0122", "Revisa el TPS")
        compose.setContent {
            RevScopeTheme {
                HealthCheckContent(HealthCheckViewModel.UiState.Done(listOf(falla), listOf("P0122"), 0L), AccionesChequeo())
            }
        }

        compose.onNodeWithText(resolverEs.get(DesignSystemR.string.ds_state_failure)).assertIsDisplayed()
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
        compose.onNodeWithText(resolverEs.get(DesignSystemR.string.ds_state_no_data)).assertIsDisplayed()
        compose.onNodeWithText(resolverEs.get(R.string.workshop_health_check_unavailable_ecu)).assertIsDisplayed()
    }

    @Test
    fun `mezcla conserva las filas no soportadas y explica el motivo`() {
        val registry = PidRegistry("[]").apply { setSupportedPids(emptySet()) }
        compose.setContent {
            RevScopeTheme {
                LiveMixtureContent(
                    readings = emptyMap(),
                    definition = registry::getDefinition,
                    disponibilidad = { pid, nombre -> DisponibilidadPid.resolver(pid, nombre, registry, resolverEs) },
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

        compose.onNodeWithText(resolverEs.get(DesignSystemR.string.ds_retry)).performClick()

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
        compose.onNodeWithContentDescription(resolverEs.get(DesignSystemR.string.ds_back)).performClick()

        assertTrue(volvio)
    }
}
