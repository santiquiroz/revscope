package com.revscope.app.navigation

import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasNavegacionTest {

    @Test
    fun barraInferior() = MatrizCaptura.componente("BarraInferior") {
        BarraInferior(rutaActual = Screen.Dashboard.route, onNavegar = {})
    }
}
