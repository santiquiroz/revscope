package com.revscope.feature.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.designsystem.LocalUnitSystem
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.feature.dashboard.gauges.textoBoost
import com.revscope.feature.dashboard.gauges.textoTemperatura
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnidadesGaugesTest {

    @get:Rule
    val compose = createComposeRule()

    private fun formatter(system: UnitSystem) = UnitFormatter(system, Locale.US)

    @Test
    fun `en sistema US los gauges muestran mph, grados F y psi`() {
        mostrarGauges(UnitSystem.US)

        compose.onNodeWithText("62").assertExists()
        compose.onNodeWithText("mph").assertExists()
        compose.onNodeWithText("194 °F").assertExists()
        compose.onNodeWithText("+2.0 psi").assertExists()
    }

    @Test
    fun `en sistema UK la temperatura sigue en grados C y la velocidad en mph`() {
        mostrarGauges(UnitSystem.UK)

        compose.onNodeWithText("mph").assertExists()
        compose.onNodeWithText("90 °C").assertExists()
        compose.onNodeWithText("+2.0 psi").assertExists()
    }

    @Test
    fun `el texto de temperatura convierte segun el sistema`() {
        assertEquals("92 °C", textoTemperatura(92f, formatter(UnitSystem.METRIC)))
        assertEquals("197 °F", textoTemperatura(92f, formatter(UnitSystem.US)))
        assertEquals("92 °C", textoTemperatura(92f, formatter(UnitSystem.UK)))
    }

    @Test
    fun `el texto de boost pasa de kPa a psi con signo`() {
        assertEquals("+12.0 kPa", textoBoost(12f, formatter(UnitSystem.METRIC), Locale.US))
        assertEquals("+1.7 psi", textoBoost(12f, formatter(UnitSystem.US), Locale.US))
        assertEquals("-2.9 psi", textoBoost(-20f, formatter(UnitSystem.UK), Locale.US))
        assertEquals("0 psi", textoBoost(0f, formatter(UnitSystem.US), Locale.US))
    }

    private fun mostrarGauges(system: UnitSystem) {
        compose.setContent {
            CompositionLocalProvider(LocalUnitSystem provides system) {
                RevScopeTheme {
                    DashboardGaugesContent(
                        estado = EstadoConducirUi(
                            conectado = true,
                            lecturas = LecturasConducir(velocidad = 100f, temperatura = 90f, boost = 13.8f),
                        ),
                    )
                }
            }
        }
    }
}
