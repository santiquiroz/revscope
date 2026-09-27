package com.revscope.feature.workshop.taller.informe

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.RevScopeTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h915dp-xhdpi")
class SemanticaInformeTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun pantallaAngostaConLetraGrande() {
        RuntimeEnvironment.setQualifiers("w360dp-h915dp-xhdpi")
        RuntimeEnvironment.setFontScale(2f)
    }

    @Test
    fun `las acciones se leen completas conservan su objetivo táctil y responden`() {
        var volver = 0
        var compartir = 0
        var imprimir = 0
        var editar = 0
        compose.setContent {
            RevScopeTheme {
                InformeContent(
                    estado = InformeUi(cargando = false, html = "<html></html>"),
                    acciones = AccionesInforme(
                        onVolver = { volver++ },
                        onCompartir = { compartir++ },
                        onImprimir = { imprimir++ },
                        onEditarInterpretacion = { editar++ },
                    ),
                    vistaPrevia = { modifier ->
                        Box(modifier) { Text("Vista previa del informe") }
                    },
                )
            }
        }

        compose.onNodeWithText("Informe de taller").assertIsDisplayed()
        compose.onNodeWithText("Vista previa del informe").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").assertAreaMinima().performClick()
        compose.onNodeWithText("Compartir").assertIsDisplayed().assertAreaMinima().performClick()
        compose.onNodeWithText("Imprimir o PDF").assertIsDisplayed().assertAreaMinima().performClick()
        compose.onNodeWithText("Editar interpretación").assertIsDisplayed().assertAreaMinima().performClick()

        assertEquals(1, volver)
        assertEquals(1, compartir)
        assertEquals(1, imprimir)
        assertEquals(1, editar)
    }

    private fun SemanticsNodeInteraction.assertAreaMinima(): SemanticsNodeInteraction =
        assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
}
