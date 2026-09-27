package com.revscope.feature.workshop.taller.modelo

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
@Config(sdk = [34], qualifiers = "w360dp-h5700dp-xhdpi")
class SemanticaModeloTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun pantallaAngostaConLetraGrande() {
        RuntimeEnvironment.setQualifiers("w360dp-h5700dp-xhdpi")
        RuntimeEnvironment.setFontScale(2f)
    }

    @Test
    fun `conocimiento conserva fuentes textos y objetivos táctiles`() {
        compose.setContent {
            RevScopeTheme {
                ConocimientoModeloContent(
                    ConocimientoModeloUi(vehiculo = "Benelli TNT 150i", modelo = ModeloBenelli.conocimiento, cargando = false),
                )
            }
        }

        compose.onNodeWithText("Delphi, tipo MT05", substring = true).assertExists()
        compose.onNodeWithText(
            "Fuente: Catálogo de partes Auteco TNT 150i, figura 34, ítem 14 (referencia); tipo MT05 del informe del caso",
        ).assertExists()
        compose.onNodeWithText("No equivalente al original").assertExists()
        compose.onNodeWithText("Verde-amarillo").assertExists()
        compose.onNodeWithContentDescription("Volver").assertAreaMinima()
        compose.onNodeWithContentDescription("Copiar referencia 280756030001").assertAreaMinima()
        compose.onNodeWithContentDescription("Editar color de Señal").assertAreaMinima()
        compose.onNodeWithText("Ver y editar referencias").assertAreaMinima()
    }

    @Test
    fun `referencias muestran origen y acciones sin depender del color`() {
        compose.setContent {
            RevScopeTheme {
                ReferenciasContent(
                    ReferenciasUi(
                        vehiculo = "Benelli TNT 150i",
                        modelo = ModeloBenelli.conocimiento.nombre,
                        bandas = ModeloBenelli.bandas,
                        cargando = false,
                    ),
                )
            }
        }

        compose.onAllNodesWithText("Típico").assertCountEquals(2)
        compose.onNodeWithText(
            "Fuente: Ejemplo sintético de la interfaz; no es una especificación Benelli ni se usa para diagnosticar",
        ).assertExists()
        compose.onNodeWithText("Editado por ti").assertExists()
        compose.onNodeWithContentDescription("Editar TPS cerrado").assertAreaMinima()
        compose.onNodeWithContentDescription("Restablecer TPS a fondo a Típico").assertAreaMinima()
    }

    private fun SemanticsNodeInteraction.assertAreaMinima() {
        assertHeightIsAtLeast(48.dp)
        assertWidthIsAtLeast(48.dp)
    }
}
