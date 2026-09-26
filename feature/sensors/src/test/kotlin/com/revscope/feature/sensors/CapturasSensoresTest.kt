package com.revscope.feature.sensors

import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.telemetry.captura.ResumenPid
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasSensoresTest {

    private val nombres = mapOf("11" to "Posición de la mariposa", "0C" to "RPM", "05" to "Temperatura del motor")

    @Test
    fun fastCaptureResumen() = MatrizCaptura.componente("FastCaptureResumen", altoMinimoDp = 1000) {
        FastCaptureResumen(
            resumen = ResumenCaptura(
                id = "c1",
                duracionMs = 38_400,
                porPid = listOf(
                    ResumenPid("11", n = 402, hz = 10.5, min = 2.35, max = 17.99, media = 9.1),
                    ResumenPid("0C", n = 380, hz = 9.9, min = 1454.0, max = 2010.0, media = 1700.0),
                    ResumenPid("05", n = 96, hz = 2.5, min = 32.0, max = 33.0, media = 32.4),
                ),
                latenciaP50Ms = 94.0,
                latenciaP95Ms = 140.0,
                motivoFin = "detenida por el usuario",
                rutaCsv = "captura.csv",
            ),
            nombreDe = { nombres[it] ?: it },
            onExportarLargo = {},
            onExportarAncho = {},
        )
    }
}
