package com.revscope.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.unit.dp

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasSistemaDisenoTest {

    @Test
    fun selectorVehiculo() = MatrizCaptura.componente("SelectorVehiculo") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorVehiculo(
                VehiculoEnEncabezado("Benelli TNT 150i", esMoto = true, "Android-Vlink", RevScopeColors.Success),
                onClick = {},
            )
            SelectorVehiculo(
                VehiculoEnEncabezado(
                    "Mazda CX-30 GT Grand Touring 2.5 Skyactiv-G automática",
                    esMoto = false,
                    "Sin conexión",
                    RevScopeColors.TextMuted,
                ),
                onClick = {},
            )
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun chipSeleccion() = MatrizCaptura.componente("ChipSeleccion") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipSeleccion("Pedal y mariposa (49, 4A, 11)", seleccionado = true, onClick = {})
            ChipSeleccion("0C RPM", seleccionado = false, onClick = {})
            ChipSeleccion("7E0 → 7E8", seleccionado = false, onClick = {}, monoespaciado = true)
        }
    }

    @Test
    fun avisoDescartable() = MatrizCaptura.componente("AvisoDescartable") {
        AvisoDescartable(
            texto = "Limitada a 10 Hz para cuidar la batería y la temperatura del teléfono",
            onDescartar = {},
        )
    }

    @Test
    fun filaEtiquetaValor() = MatrizCaptura.componente("FilaEtiquetaValor") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilaEtiquetaValor("Presión de combustible del riel", "412,5 kPa")
            FilaEtiquetaValor("Ajuste largo de combustible (banco 1)", "+2,3 %")
        }
    }
}
