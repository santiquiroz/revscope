package com.revscope.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasAjustesTest {

    @Test
    fun mode22Acciones() = MatrizCaptura.componente("Mode22Acciones") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Mode22Acciones(
                escaneando = false,
                vigilando = false,
                puedeEscanear = true,
                puedeVigilar = true,
                puedeLimpiar = true,
                onEscanearODetener = {},
                onVigilar = {},
                onLimpiar = {},
            )
            Mode22Acciones(
                escaneando = false,
                vigilando = true,
                puedeEscanear = true,
                puedeVigilar = false,
                puedeLimpiar = true,
                onEscanearODetener = {},
                onVigilar = {},
                onLimpiar = {},
            )
            FilaObjetivo(header = "7E0", onVolverAEcu = {})
        }
    }
}
