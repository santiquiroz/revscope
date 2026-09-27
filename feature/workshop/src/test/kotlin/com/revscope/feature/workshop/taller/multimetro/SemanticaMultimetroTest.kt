package com.revscope.feature.workshop.taller.multimetro

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.RevScopeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h5600dp-xhdpi")
class SemanticaMultimetroTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun configurarPantallaAngostaConLetraGrande() {
        RuntimeEnvironment.setQualifiers("w360dp-h5600dp-xhdpi")
        RuntimeEnvironment.setFontScale(2f)
    }

    @Test
    fun `el TPS Benelli usa tarjetas sin recortar textos y conserva áreas táctiles`() {
        compose.setContent {
            RevScopeTheme {
                MultimetroContent(MultimetroBenelli.ui(), AccionesMultimetro())
            }
        }

        compose.onNodeWithText("TPS de 3 cables").assertExists()
        compose.onAllNodesWithText("Referencia 5", substring = true)[0].assertExists()
        compose.onAllNodesWithText("Verde-amarillo").assertCountEquals(2)
        compose.onNodeWithText("Referencia y masa correctas; señal baja en todo el recorrido").assertExists()
        compose.onNodeWithText("Contra lo que recibe la ECU").assertExists()
        compose.onAllNodesWithText("Cerrado").assertCountEquals(3)

        descripcionesCampos().forEach {
            compose.onNodeWithContentDescription(it).assertAreaMinima()
        }
        compose.onNodeWithContentDescription("Volver").assertAreaMinima()
        compose.onNodeWithContentDescription("Sensor de posición del acelerador (TPS)").assertAreaMinima()
        compose.onNodeWithContentDescription("Editar el color de Referencia 5\u00a0V").assertAreaMinima()
        compose.onNodeWithContentDescription("Editar el color de Masa").assertAreaMinima()
        compose.onNodeWithContentDescription("Editar el color de Señal").assertAreaMinima()
        compose.onNodeWithText("Guardar en la sesión").assertAreaMinima()
    }

    private fun descripcionesCampos(): List<String> {
        val cables = listOf(
            "Referencia 5\u00a0V (Rojo)",
            "Masa (Negro)",
            "Señal (Verde-amarillo)",
        )
        val condiciones = listOf("Cerrado", "Medio", "A fondo")
        return cables.flatMap { cable -> condiciones.map { condicion -> "$cable, $condicion, en V" } }
    }

    private fun SemanticsNodeInteraction.assertAreaMinima() {
        assertHeightIsAtLeast(48.dp)
        assertWidthIsAtLeast(48.dp)
    }
}
