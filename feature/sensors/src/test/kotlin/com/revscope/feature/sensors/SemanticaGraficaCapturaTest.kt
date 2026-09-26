package com.revscope.feature.sensors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.taller.grafica.EstadoVref
import com.revscope.core.obd.taller.grafica.UnidadPosicion
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticaGraficaCapturaTest {

    @get:Rule
    val compose = createComposeRule()

    private fun ui(unidad: UnidadPosicion, pausada: Boolean) = MapeoGraficaCaptura.de(
        EntradaGraficaCaptura(
            series = CapturaBenelli.series(),
            nombre = CapturaBenelli::nombre,
            unidadPid = CapturaBenelli::unidad,
            hz = emptyMap(),
            unidad = unidad,
            referencia = EstadoVref(ReferenciaVoltaje.TIPICA, null, null),
            ventanaMs = 10_000,
            pausada = pausada,
            bandas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList()),
        ),
    )

    @Test
    fun `el conmutador de unidad y la pausa son controles con nombre y estado`() {
        var unidad by mutableStateOf(UnidadPosicion.PORCENTAJE)
        var pausada by mutableStateOf(false)
        compose.setContent {
            RevScopeTheme {
                GraficaCapturaContent(ui(unidad, pausada), AccionesGrafica(onUnidad = { unidad = it }, onPausa = { pausada = it }))
            }
        }

        compose.onNodeWithText("En %").assertIsSelected()
        compose.onNodeWithText("En voltios").performClick().assertIsSelected()
        compose.onNodeWithText("En %").assertIsNotSelected()
        compose.onNodeWithText(TEXTO_PAUSAR).performClick()
        compose.onNodeWithText(TEXTO_REANUDAR).assertExists()
        compose.onNodeWithText("Gráfica en pausa: la captura sigue grabando").assertExists()

        assertEquals(UnidadPosicion.VOLTIOS to true, unidad to pausada)
    }

    @Test
    fun `la grafica tiene un resumen accesible con el valor en las dos unidades`() {
        compose.setContent { RevScopeTheme { GraficaCapturaContent(ui(UnidadPosicion.VOLTIOS, false), AccionesGrafica()) } }

        compose.onNodeWithContentDescription("11 Posición de la mariposa: 0,47 V (9,4 %), estable", substring = true)
            .assertExists()
    }
}
