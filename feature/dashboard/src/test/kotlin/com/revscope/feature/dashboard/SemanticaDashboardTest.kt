package com.revscope.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.session.EstadoViaje
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `finalizar viaje GPS pide confirmacion y confirmar llama al callback`() {
        var finalizado = false
        compose.setContent {
            RevScopeTheme {
                DashboardContent(
                    estado = EstadoConducirUi(ofrecerViajeGps = true, viajeGpsActivo = true),
                    acciones = AccionesConducir(onFinalizarViajeGps = { finalizado = true }),
                )
            }
        }

        compose.onNodeWithText("Finalizar viaje GPS").performClick()

        compose.onNodeWithText("¿Finalizar el viaje?").assertIsDisplayed()
        compose.onNodeWithText("El viaje GPS se guarda en el historial.").assertIsDisplayed()
        compose.onNodeWithText("Finalizar viaje").performClick()
        assertTrue(finalizado)
    }

    @Test
    fun `descartar finalizacion del viaje GPS no llama al callback`() {
        var finalizado = false
        compose.setContent {
            RevScopeTheme {
                DashboardContent(
                    estado = EstadoConducirUi(ofrecerViajeGps = true, viajeGpsActivo = true),
                    acciones = AccionesConducir(onFinalizarViajeGps = { finalizado = true }),
                )
            }
        }

        compose.onNodeWithText("Finalizar viaje GPS").performClick()
        compose.onNodeWithText("Seguir grabando").performClick()

        assertFalse(finalizado)
        compose.onNodeWithText("¿Finalizar el viaje?").assertDoesNotExist()
    }

    @Test
    fun `desconectar mientras graba pide confirmacion y confirmar llama al callback`() {
        var desconectado = false
        compose.setContent {
            RevScopeTheme {
                ObdTripControls(
                    estado = EstadoViaje.Grabando(sessionId = 1, inicioMs = 0),
                    aviso = null,
                    onFinalizar = {},
                    onIniciar = {},
                    onDesconectar = { desconectado = true },
                    expandidoInicial = true,
                )
            }
        }

        compose.onNodeWithText("Desconectar adaptador").performClick()

        compose.onNodeWithText("¿Desconectar el adaptador?").assertIsDisplayed()
        compose.onNodeWithText("También se cierra el viaje en curso; queda guardado en el historial.").assertIsDisplayed()
        compose.onNodeWithText("Desconectar").performClick()
        assertTrue(desconectado)
    }

    @Test
    fun `cancelar desconexion mientras graba no llama al callback`() {
        var desconectado = false
        compose.setContent {
            RevScopeTheme {
                ObdTripControls(
                    estado = EstadoViaje.Grabando(sessionId = 1, inicioMs = 0),
                    aviso = null,
                    onFinalizar = {},
                    onIniciar = {},
                    onDesconectar = { desconectado = true },
                    expandidoInicial = true,
                )
            }
        }

        compose.onNodeWithText("Desconectar adaptador").performClick()
        compose.onNodeWithText("Cancelar").performClick()

        assertFalse(desconectado)
        compose.onNodeWithText("¿Desconectar el adaptador?").assertDoesNotExist()
    }

    @Test
    fun `desconectar sin viaje sigue siendo directo`() {
        var desconectado = false
        compose.setContent {
            RevScopeTheme {
                ObdTripControls(
                    estado = EstadoViaje.EnlaceSinViaje,
                    aviso = null,
                    onFinalizar = {},
                    onIniciar = {},
                    onDesconectar = { desconectado = true },
                    expandidoInicial = true,
                )
            }
        }

        compose.onNodeWithText("Desconectar adaptador").performClick()

        assertTrue(desconectado)
        compose.onNodeWithText("¿Desconectar el adaptador?").assertDoesNotExist()
    }

    private companion object {
        const val VALOR_SIN_DATOS = "—"
        const val MOTIVO_SIN_ADAPTADOR = "Sin adaptador"
    }
}
