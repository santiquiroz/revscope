package com.revscope.feature.dtc

import androidx.compose.ui.graphics.Color
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasDtcTest {

    @Test
    fun statusContent() = MatrizCaptura.componente("DtcStatusContent") {
        StatusContent("Sin códigos activos, pendientes ni permanentes ✓", Color(0xFF00E676))
    }
}
