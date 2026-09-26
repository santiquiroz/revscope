package com.revscope.feature.workshop.taller.prueba

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticaPruebaGuiadaTest {

    @get:Rule
    val compose = createComposeRule()

    private val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles)

    private fun ui(estado: EstadoPrueba, dialogo: DialogoPrueba? = null) = PruebaGuiadaUi(
        fase = pantalla.fase(EntradaPantalla(estado, serieVivo = BarridoBenelli.serieMedio(), bandas = BarridoBenelli.bandas)),
        dialogo = dialogo,
    )

    private fun regionViva(texto: String) = SemanticsMatcher("región viva «$texto»") { nodo ->
        nodo.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite &&
            nodo.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(texto) == true
    }

    @Test
    fun `el cambio de paso se anuncia en una region viva y la cuenta solo en los ultimos 3 s`() {
        var estado by mutableStateOf(ui(BarridoBenelli.enPaso(0, FasePaso.SOSTENIENDO, 400)))
        compose.setContent { RevScopeTheme { PruebaGuiadaContent(estado, AccionesPrueba()) } }
        compose.onNode(regionViva("1")).assertIsDisplayed()

        estado = ui(BarridoBenelli.enPaso(1, FasePaso.POSICIONANDO, null))
        compose.onNode(regionViva("Paso 2 de 5: Medio. Posiciona el acelerador y toca Listo")).assertIsDisplayed()

        estado = ui(BarridoBenelli.enPaso(1, FasePaso.SOSTENIENDO, 4_600))
        compose.onNode(regionViva("Paso 2 de 5: Medio. Sosteniendo")).assertIsDisplayed()

        estado = ui(BarridoBenelli.enPaso(1, FasePaso.SOSTENIENDO, 2_400))
        compose.onNode(regionViva("3")).assertIsDisplayed()
    }

    @Test
    fun `el boton de cancelar tiene nombre y con pasos grabados pide confirmar antes de descartar`() {
        var pedidas = 0
        var confirmadas = 0
        var estado by mutableStateOf(ui(BarridoBenelli.enPaso(2, FasePaso.POSICIONANDO, null)))
        compose.setContent {
            RevScopeTheme {
                PruebaGuiadaContent(estado, AccionesPrueba(onPedirCancelar = { pedidas++ }, onConfirmarCancelar = { confirmadas++ }))
            }
        }

        compose.onNodeWithContentDescription(TextosPrueba.CANCELAR).assertHasClickAction().performClick()
        estado = estado.copy(dialogo = DialogoPrueba.CANCELAR)

        compose.onNodeWithText("¿Descartar la prueba?").assertIsDisplayed()
        compose.onNodeWithText("2 de 5 pasos ya hechos no se guardan en la sesión.").assertIsDisplayed()
        compose.onNodeWithText("Seguir con la prueba").assertIsDisplayed()
        compose.onNodeWithText("Descartar").performClick()
        assertEquals(1 to 1, pedidas to confirmadas)
    }

    @Test
    fun `cada fase tiene una sola accion principal y al sostener ninguna`() {
        var avanzar = 0
        var estado by mutableStateOf(ui(BarridoBenelli.enPaso(1, FasePaso.POSICIONANDO, null)))
        compose.setContent { RevScopeTheme { PruebaGuiadaContent(estado, AccionesPrueba(onAvanzar = { avanzar++ })) } }

        compose.onAllNodesWithText(TextosPrueba.ACCION_LISTO).assertCountEquals(1)
        compose.onNodeWithText(TextosPrueba.ACCION_LISTO).performClick()
        estado = ui(BarridoBenelli.enPaso(1, FasePaso.SOSTENIENDO, 3_000))

        compose.onAllNodesWithText(TextosPrueba.ACCION_LISTO).assertCountEquals(0)
        compose.onNodeWithText(TEXTO_REPETIR_PASO).assertExists()
        assertEquals(1, avanzar)
    }

    @Test
    fun `con una precondicion fallando no se puede empezar y dice que hacer`() {
        compose.setContent {
            RevScopeTheme {
                PruebaGuiadaContent(
                    PruebaGuiadaUi(
                        pantalla.fase(EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.TPS_BARRIDO, BarridoBenelli.precondicionesUnaFallando)),
                    ),
                    AccionesPrueba(),
                )
            }
        }

        compose.onNodeWithText("Empezar prueba").assertIsNotEnabled()
        compose.onNodeWithText("Resuelve lo marcado para empezar.").assertIsDisplayed()
        compose.onNodeWithContentDescription("No cumple").assertIsDisplayed()
    }

    @Test
    fun `el resultado sin sesion ofrece guardarlo y abre la guia del codigo`() {
        var guia: String? = null
        var guardar = 0
        compose.setContent {
            RevScopeTheme {
                PruebaGuiadaContent(
                    PruebaGuiadaUi(pantalla.fase(EntradaPantalla(BarridoBenelli.terminada(eventoId = null)))),
                    AccionesPrueba(onVerGuia = { guia = it }, onGuardarEnSesion = { guardar++ }),
                )
            }
        }

        compose.onNodeWithText("Guardar en una sesión nueva").performScrollTo().performClick()
        compose.onNodeWithText("Ver guía P0122").performScrollTo().performClick()

        assertEquals(1, guardar)
        assertEquals("P0122", guia)
    }
}
