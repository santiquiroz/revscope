package com.revscope.feature.vehicle

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revscope.core.data.db.entities.VehicleProfileEntity
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
class SemanticaVehiculoTest {

    @get:Rule
    val compose = createComposeRule()

    private val benelli = VehicleProfileEntity(
        id = 3,
        name = "Benelli TNT 150i",
        type = "MOTORCYCLE",
        vin = null,
        enabledPids = "0C,0D,05",
        gearRatios = null,
        createdAt = 0,
    )

    private fun montar(borrados: MutableList<Long>) {
        compose.setContent {
            RevScopeTheme {
                PerfilesGuardados(
                    perfiles = listOf(benelli),
                    estado = EstadoPerfiles(editandoId = null, activoId = null, adaptadorConectado = null),
                    onEditar = {},
                    onActivar = {},
                    onBorrar = { borrados += it.id },
                )
            }
        }
    }

    @Test
    fun `eliminar un vehiculo pide confirmacion y dice que los viajes se conservan`() {
        val borrados = mutableListOf<Long>()
        montar(borrados)

        compose.onNodeWithContentDescription("Eliminar Benelli TNT 150i").performClick()

        compose.onNodeWithText("¿Eliminar «Benelli TNT 150i»?").assertIsDisplayed()
        compose.onNodeWithText("Los viajes de este vehículo se conservan en el historial (filtro «Todos»).")
            .assertIsDisplayed()
        assertTrue(borrados.isEmpty())

        compose.onNodeWithText("Eliminar vehículo").performClick()

        assertEquals(listOf(3L), borrados)
    }

    @Test
    fun `cancelar no elimina el vehiculo`() {
        val borrados = mutableListOf<Long>()
        montar(borrados)

        compose.onNodeWithContentDescription("Eliminar Benelli TNT 150i").performClick()
        compose.onNodeWithText("Cancelar").performClick()

        assertTrue(borrados.isEmpty())
    }
}
