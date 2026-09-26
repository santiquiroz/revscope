package com.revscope.feature.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.designsystem.RevScopeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticaViajesTest {

    @get:Rule
    val compose = createComposeRule()

    private val viaje = SessionEntity(
        id = 7,
        vehicleProfileId = 1,
        startedAt = 1_790_000_000_000,
        endedAt = 1_790_000_000_000 + 30 * 60_000,
        adapterName = "Android-Vlink",
        maxRpm = 9_000,
        maxSpeed = 80,
        distanceKm = 12.3f,
    )

    private fun montar(sesiones: List<SessionEntity>, borrados: MutableList<Long>, onIniciar: () -> Unit = {}) {
        compose.setContent {
            RevScopeTheme {
                SessionHistoryContent(
                    historial = HistorialUi(sesiones, emptyList(), filtro = null, candidatoComparar = null),
                    acciones = AccionesHistorial(
                        onAbrir = {},
                        onComparar = {},
                        onBorrar = { borrados += it },
                        onFiltrar = {},
                        onIniciarViaje = onIniciar,
                    ),
                )
            }
        }
    }

    @Test
    fun `borrar un viaje pide confirmacion con su fecha antes de borrar`() {
        val borrados = mutableListOf<Long>()
        montar(listOf(viaje), borrados)

        compose.onNodeWithContentDescription("Borrar viaje").performClick()

        compose.onNodeWithText("¿Borrar el viaje?").assertIsDisplayed()
        compose.onNodeWithText(descripcionViaje(viaje)).assertIsDisplayed()
        assertTrue(borrados.isEmpty())

        compose.onNodeWithText("Borrar viaje").performClick()

        assertEquals(listOf(7L), borrados)
    }

    @Test
    fun `cancelar no borra el viaje`() {
        val borrados = mutableListOf<Long>()
        montar(listOf(viaje), borrados)

        compose.onNodeWithContentDescription("Borrar viaje").performClick()
        compose.onNodeWithText("Cancelar").performClick()

        assertTrue(borrados.isEmpty())
    }

    @Test
    fun `sin viajes muestra el estado vacio con la accion de iniciar un viaje`() {
        var iniciado = false
        montar(emptyList(), mutableListOf(), onIniciar = { iniciado = true })

        compose.onNodeWithText("Iniciar un viaje").performClick()

        assertTrue(iniciado)
    }

    @Test
    fun `comparar tiene nombre accesible en español`() {
        montar(listOf(viaje), mutableListOf())

        compose.onNodeWithContentDescription("Comparar este viaje").assertIsDisplayed()
    }
}
