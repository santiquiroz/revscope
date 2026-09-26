package com.revscope.feature.sensors

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.grafica.EstadoVref
import com.revscope.core.obd.taller.grafica.UnidadPosicion
import com.revscope.core.obd.taller.grafica.VrefSesion
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.referencia.ResolutorBandas
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

    private fun grafica(unidad: UnidadPosicion, pausada: Boolean = false) = MapeoGraficaCaptura.de(
        EntradaGraficaCaptura(
            series = CapturaBenelli.series(),
            nombre = CapturaBenelli::nombre,
            unidadPid = CapturaBenelli::unidad,
            hz = mapOf("11" to 10.4, "0C" to 9.8),
            unidad = unidad,
            referencia = EstadoVref(ReferenciaVoltaje.TIPICA, null, null),
            ventanaMs = 30_000,
            pausada = pausada,
            bandas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList()),
        ),
    )

    private fun soloTps(unidad: UnidadPosicion) = MapeoGraficaCaptura.de(
        EntradaGraficaCaptura(
            series = CapturaBenelli.series(conRpm = false),
            nombre = CapturaBenelli::nombre,
            unidadPid = CapturaBenelli::unidad,
            hz = mapOf("11" to 10.4),
            unidad = unidad,
            referencia = EstadoVref(ReferenciaVoltaje(4.96, VrefSesion.ORIGEN), ReferenciaVoltaje(4.96, VrefSesion.ORIGEN), null),
            ventanaMs = 10_000,
            pausada = false,
            bandas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList()),
        ),
    )

    @Test
    fun graficaCapturaEnPorcentaje() = MatrizCaptura.componente("GraficaCapturaContent_pct", altoMinimoDp = 1400) {
        GraficaCapturaContent(grafica(UnidadPosicion.PORCENTAJE), AccionesGrafica())
    }

    @Test
    fun graficaCapturaEnVoltiosEnPausa() = MatrizCaptura.componente("GraficaCapturaContent_v_pausa", altoMinimoDp = 1400) {
        GraficaCapturaContent(grafica(UnidadPosicion.VOLTIOS, pausada = true), AccionesGrafica())
    }

    @Test
    fun graficaCapturaTpsEnVoltiosConBandas() = MatrizCaptura.componente("GraficaCapturaContent_tps_v", altoMinimoDp = 1400) {
        GraficaCapturaContent(soloTps(UnidadPosicion.VOLTIOS), AccionesGrafica())
    }

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
