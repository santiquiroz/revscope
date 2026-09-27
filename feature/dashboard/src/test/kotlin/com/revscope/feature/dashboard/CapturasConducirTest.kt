package com.revscope.feature.dashboard

import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.SelectorVehiculo
import com.revscope.core.designsystem.VehiculoEnEncabezado
import com.revscope.core.intelligence.efficiency.DriveStyle
import com.revscope.core.intelligence.efficiency.TripScore
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.uitesting.MatrizCaptura
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasConducirTest {

    private val benelli = VehiculoEnEncabezado(
        nombre = "Benelli TNT 150i",
        esMoto = true,
        estadoEnlace = "Sin conexión",
        colorEstado = RevScopeColors.TextMuted,
    )

    @Test
    fun configureAdapterCta() = MatrizCaptura.componente("ConfigureAdapterCta") {
        ConfigureAdapterCta(onClick = {})
    }

    @Test
    fun dashboardGpsHeroContent() = MatrizCaptura.pantalla("DashboardGpsHeroContent", altoMinimoDp = 1800) {
        DashboardContent(
            estado = EstadoConducirUi(ofrecerViajeGps = true, modoGpsHero = true),
            acciones = AccionesConducir(),
        )
    }

    @Test
    fun dashboardConectadoContent() = MatrizCaptura.pantalla("DashboardConectadoContent", altoMinimoDp = 1800) {
        DashboardContent(
            estado = EstadoConducirUi(
                conectado = true,
                estadoViaje = EstadoViaje.Grabando(sessionId = 1, inicioMs = 0),
                lecturas = LecturasConducir(rpm = 7250f, velocidad = 128f, temperatura = 92f, boost = 12f, marcha = 5),
                escalas = EscalasConducir(maxRpm = 11000, redlineRpm = 9500, maxVelocidad = 299, marchas = 6),
                puntaje = TripScore(62, DriveStyle.NORMAL, 4f, 2, 41f),
            ),
            acciones = AccionesConducir(),
        )
    }

    @Test
    fun dashboardGaugesContentSinDatos() =
        MatrizCaptura.componente("DashboardGaugesContent_sin_datos", altoMinimoDp = 1400) {
            DashboardGaugesContent(
                estado = EstadoConducirUi(
                    lecturas = LecturasConducir(
                        motivoRpm = "Sin adaptador",
                        motivoVelocidad = "Sin adaptador",
                        motivoTemperatura = "Sin adaptador",
                        motivoBoost = "Sin adaptador",
                        motivoMarcha = "Sin adaptador",
                    ),
                ),
            )
        }

    @Test
    fun encabezadoConducir() = MatrizCaptura.componente("EncabezadoConducir") {
        DashboardTopBar(
            enlace = EnlaceAdaptador.SIN_ADAPTADOR,
            voltaje = 12.2,
            selectorVehiculo = { SelectorVehiculo(benelli, onClick = {}) },
            onAdaptador = {},
            onModoPista = {},
            onAjustes = {},
        )
    }

    @Test
    fun encabezadoConducirNombreLargo() = MatrizCaptura.componente("EncabezadoConducirNombreLargo") {
        DashboardTopBar(
            enlace = EnlaceAdaptador.CONECTADO,
            voltaje = 11.4,
            selectorVehiculo = {
                SelectorVehiculo(
                    benelli.copy(nombre = "Mazda CX-30 GT Grand Touring 2.5", esMoto = false),
                    onClick = {},
                )
            },
            onAdaptador = {},
            onModoPista = {},
            onAjustes = {},
        )
    }

    @Test
    fun updateBanner() = MatrizCaptura.componente("UpdateBanner") {
        UpdateBanner(version = "1.21.0", onDownload = {}, onDismiss = {})
    }

    @Test
    fun tripScoreBar() = MatrizCaptura.componente("TripScoreBar") {
        TripScoreBar(tripScore = TripScore(38, DriveStyle.AGGRESSIVE, 20f, 9, 70f))
    }

    @Test
    fun obdTripControls() = MatrizCaptura.componente("ObdTripControls") {
        ObdTripControls(
            estado = EstadoViaje.Grabando(sessionId = 1, inicioMs = 0),
            aviso = "Viaje guardado: puedes leer los códigos de falla sin reconectar",
            onFinalizar = {},
            onIniciar = {},
            onDesconectar = {},
            expandidoInicial = true,
        )
    }

    @Test
    fun bannerPeligro() = MatrizCaptura.componente("BannerPeligro") {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            BannerPeligro("El SOAT vence en 3 días", onClick = {})
            BannerPeligro("Temperatura del motor alta: 112 °C")
        }
    }
}
