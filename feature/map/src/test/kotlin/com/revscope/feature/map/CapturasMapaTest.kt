package com.revscope.feature.map

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
class CapturasMapaTest {

    @Test
    fun errorBanner() = MatrizCaptura.componente("ErrorBanner") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ErrorBanner(message = "Sin ruta", onDismiss = {})
            ErrorBanner(
                message = "No se pudo calcular la ruta: el servidor de rutas no respondió a tiempo. " +
                    "Revisa la conexión a internet e inténtalo de nuevo.",
                onDismiss = {},
            )
        }
    }
}
