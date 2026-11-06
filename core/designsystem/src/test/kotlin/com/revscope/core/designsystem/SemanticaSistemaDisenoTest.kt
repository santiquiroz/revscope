package com.revscope.core.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticaSistemaDisenoTest {

    private val es = XmlStringResolver.es(R::class.java)

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setSpanishLocale() {
        RuntimeEnvironment.setQualifiers("es")
    }

    @Test
    fun `NivelBadge expone su texto y no depende del color`() {
        compose.setContent { RevScopeTheme { NivelBadge(NivelEstado.FALLA) } }

        compose.onNodeWithText(es.get(R.string.ds_state_failure)).assertIsDisplayed()
    }

    @Test
    fun `NivelBadge admite un texto propio`() {
        compose.setContent { RevScopeTheme { NivelBadge(NivelEstado.ATENCION, texto = "Vence en 12 días") } }

        compose.onNodeWithText("Vence en 12 días").assertIsDisplayed()
    }

    @Test
    fun `ErrorState reintenta de verdad`() {
        var reintentos = 0
        compose.setContent { RevScopeTheme { ErrorState("Sin respuesta", onReintentar = { reintentos++ }) } }

        compose.onNodeWithText(es.get(R.string.ds_retry)).performClick()

        assertEquals(1, reintentos)
    }

    @Test
    fun `EmptyState muestra el mensaje y su accion`() {
        var abierto = false
        compose.setContent {
            RevScopeTheme { EmptyState("Sin viajes", accion = AccionEstado("Iniciar un viaje") { abierto = true }) }
        }

        compose.onNodeWithText("Sin viajes").assertIsDisplayed()
        compose.onNodeWithText("Iniciar un viaje").performClick()

        assertTrue(abierto)
    }

    @Test
    fun `BarraConVolver tiene Volver con nombre en español`() {
        var volvio = false
        compose.setContent { RevScopeTheme { BarraConVolver("Códigos DTC", onVolver = { volvio = true }) } }

        compose.onNodeWithContentDescription(es.get(R.string.ds_back)).performClick()

        assertTrue(volvio)
    }

    @Test
    fun `el dialogo destructivo cancela sin confirmar`() {
        var confirmado = false
        var cancelado = false
        compose.setContent {
            RevScopeTheme {
                ConfirmarDestructivoDialog(
                    titulo = "¿Borrar?",
                    textoConfirmar = "Borrar",
                    onConfirmar = { confirmado = true },
                    onCancelar = { cancelado = true },
                )
            }
        }

        compose.onNodeWithText(es.get(R.string.ds_cancel)).performClick()

        assertTrue(cancelado)
        assertFalse(confirmado)
    }

    @Test
    fun `el texto del paso dice cual de cuantos`() {
        val resolverEs = XmlStringResolver.es(R::class.java)
        assertEquals("Paso 2 de 4", textoPaso(2, 4, resolverEs))
        assertEquals("Paso 4 de 4: A fondo", textoPaso(9, 4, resolverEs, "A fondo"))
    }

    @Test
    fun `NivelBadge usa el recurso ingles`() {
        RuntimeEnvironment.setQualifiers("en")
        compose.setContent { RevScopeTheme { NivelBadge(NivelEstado.FALLA) } }

        compose.onNodeWithText(XmlStringResolver.en(R::class.java).get(R.string.ds_state_failure)).assertIsDisplayed()
    }
}
