package com.revscope.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.designsystem.RevScopeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticaDashboardTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `los gauges sin datos muestran guion y motivo en vez de cero`() {
        compose.setContent {
            RevScopeTheme {
                DashboardGaugesContent(
                    estado = EstadoConducirUi(
                        lecturas = LecturasConducir(
                            motivoRpm = "Sin adaptador",
                            motivoVelocidad = "Sin adaptador",
                            motivoTemperatura = "Sin adaptador",
                            motivoBoost = "Sin adaptador",
                            motivoMarcha = "Sin adaptador",
                        ),
                    ),
                )
            }
        }

        compose.onAllNodesWithText(VALOR_SIN_DATOS).assertCountEquals(5)
        compose.onAllNodesWithText(MOTIVO_SIN_ADAPTADOR).assertCountEquals(5)
        compose.onNodeWithText("0 °C").assertDoesNotExist()
    }

    private companion object {
        const val VALOR_SIN_DATOS = "—"
        const val MOTIVO_SIN_ADAPTADOR = "Sin adaptador"
    }
}
