package com.revscope.feature.sensors

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.taller.pid.DisponibilidadPid
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticaPidsNoSoportadosTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `captura marca un PID no soportado y conserva el motivo`() {
        val pid = PidDefinition(
            mode = "01",
            pid = "11",
            name = "Throttle position",
            nameEs = "Posición de la mariposa",
            bytes = 1,
            formula = "A*100/255",
            unit = "%",
            min = 0.0,
            max = 100.0,
            priority = 1,
        )
        val disponibilidad = DisponibilidadPid.resolver(
            pid = pid.pid,
            nombre = pid.nameEs.lowercase(),
            estado = EstadoSoporte.NoSoportado,
        )

        compose.setContent {
            RevScopeTheme {
                SelectorPids(
                    seleccion = emptyList(),
                    candidatos = listOf(pid),
                    disponibilidad = { disponibilidad },
                    onElegirPedalYMariposa = {},
                    onAlternar = {},
                )
            }
        }

        compose.onNodeWithText("11 Posición de la mariposa · no disponible").assertExists()
        assertEquals("Esta ECU no reporta posición de la mariposa (PID 11).", disponibilidad.motivo)
    }

    @Test
    fun `captura explica persistentemente el PID no disponible`() {
        val disponibilidad = DisponibilidadPid.resolver(
            pid = "11",
            nombre = "posición de la mariposa",
            estado = EstadoSoporte.NoSoportado,
        )

        compose.setContent {
            RevScopeTheme { AvisoPidNoDisponible(disponibilidad) }
        }

        compose.onNodeWithText("No disponible en esta ECU").assertIsDisplayed()
        compose.onNodeWithText("Esta ECU no reporta posición de la mariposa (PID 11).").assertIsDisplayed()
    }
}
