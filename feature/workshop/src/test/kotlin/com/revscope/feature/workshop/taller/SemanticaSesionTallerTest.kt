package com.revscope.feature.workshop.taller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.connection.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticaSesionTallerTest {

    @get:Rule
    val compose = createComposeRule()

    private fun hub(abierta: Boolean, conexion: ConnectionState = CasoBenelli.conectado) =
        ResumenHub.de(CasoBenelli.datosHub(abierta), conexion, verTodas = false, zona = BOGOTA)

    @Test
    fun `sin sesión la CTA inicia el diagnóstico y con sesión la continúa`() {
        var iniciar = 0
        var abierta: Long? = null
        var estado by mutableStateOf(hub(abierta = false))
        compose.setContent {
            RevScopeTheme {
                TallerHubContent(estado, AccionesHub(onIniciarDiagnostico = { iniciar++ }, onAbrirSesion = { abierta = it }))
            }
        }

        compose.onNodeWithText("Iniciar diagnóstico").performClick()
        estado = hub(abierta = true)
        compose.onNodeWithText("Continuar sesión").performClick()

        assertEquals(1, iniciar)
        assertEquals(CasoBenelli.sesion.id, abierta)
    }

    @Test
    fun `sin adaptador las herramientas que lo requieren lo dicen y no abren`() {
        var codigos = 0
        compose.setContent {
            RevScopeTheme {
                TallerHubContent(hub(abierta = false, ConnectionState.Disconnected), AccionesHub(onCodigos = { codigos++ }))
            }
        }

        compose.onNodeWithText("Códigos").performClick()

        assertEquals(0, codigos)
        compose.onAllNodesWithText("Requiere adaptador")[0].assertIsDisplayed()
        compose.onNodeWithText("Conectar").assertIsDisplayed()
    }

    @Test
    fun `la sesión tiene una sola CTA y el menú accesible lleva a eliminar con lo que se pierde`() {
        var pedida = 0
        compose.setContent {
            RevScopeTheme {
                SesionTallerContent(
                    CasoBenelli.estadoSesion(UiSesion(menu = true)),
                    AccionesSesion(onPedirEliminacion = { pedida++ }),
                )
            }
        }

        compose.onAllNodesWithText("Agregar a la sesión").assertCountEquals(1)
        compose.onNodeWithContentDescription("Más opciones de la sesión").assertIsDisplayed()
        compose.onNodeWithText("Eliminar sesión").performClick()
        assertEquals(1, pedida)
    }

    @Test
    fun `el diálogo de eliminar dice cuántos eventos y archivos se pierden`() {
        var confirmada = false
        compose.setContent {
            RevScopeTheme {
                SesionTallerContent(
                    CasoBenelli.estadoSesion(UiSesion(dialogo = DialogoSesion.ELIMINAR)),
                    AccionesSesion(onConfirmarEliminacion = { confirmada = true }),
                )
            }
        }

        compose.onNodeWithText("Se pierden los síntomas, las notas, la comparación y los 6 eventos de la línea de tiempo.")
            .assertIsDisplayed()
        compose.onNodeWithText("Se borra 1 archivo CSV de capturas guardado en la sesión.").assertIsDisplayed()
        compose.onNodeWithText("Eliminar sesión").performClick()
        assertTrue(confirmada)
    }

    @Test
    fun `ver detalle despliega la hora exacta y el origen`() {
        val captura = CasoBenelli.estadoSesion().eventos.first { it.id == 3L }
        var abierto by mutableStateOf(false)
        compose.setContent {
            RevScopeTheme { EventoLinea(captura, ultimo = false, abierto = abierto, onAlternar = { abierto = !abierto }) }
        }

        compose.onNodeWithText("Ver detalle").performClick()

        compose.onNodeWithText("Hora exacta: 25 sep 2026, 20:14:00").assertIsDisplayed()
        compose.onNodeWithText("Anotado por la app").assertIsDisplayed()
        compose.onNodeWithText("Ocultar detalle").assertIsDisplayed()
    }
}
