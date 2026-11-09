package com.revscope.feature.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.designsystem.SelectorVehiculo
import com.revscope.core.designsystem.VehiculoEnEncabezado
import com.revscope.core.intelligence.efficiency.DriveStyle
import com.revscope.core.intelligence.efficiency.TripScore
import com.revscope.core.obd.session.EstadoViaje
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Captura en inglés de Conducir para el README (docs/screenshots/en/drive.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasReadmeDriveTest {

    private val benelli = VehiculoEnEncabezado(
        nombre = "Benelli TNT 150i",
        esMoto = true,
        estadoEnlace = "Android-Vlink",
        colorEstado = RevScopeColors.Success,
    )

    // Mismas lecturas que CapturasConducirTest.dashboardConectadoContent, con las RPM al 96 % de
    // la línea roja para que se vea el shift light (borde Accent, igual que DashboardScreen).
    private val estado = EstadoConducirUi(
        conectado = true,
        estadoViaje = EstadoViaje.Grabando(sessionId = 1, inicioMs = 0),
        lecturas = LecturasConducir(rpm = 9150f, velocidad = 128f, temperatura = 92f, boost = 12f, marcha = 5),
        escalas = EscalasConducir(maxRpm = 11000, redlineRpm = 9500, maxVelocidad = 299, marchas = 6),
        puntaje = TripScore(62, DriveStyle.NORMAL, 4f, 2, 41f),
    )

    @Test
    fun drive() {
        RuntimeEnvironment.setQualifiers("en-w412dp-h915dp-xxhdpi")
        RuntimeEnvironment.setFontScale(1.0f)
        captureRoboImage(filePath = "../../docs/screenshots/en/drive.png") {
            RevScopeTheme {
                Scaffold(
                    modifier = Modifier.border(6.dp, RevScopeColors.Accent),
                    contentWindowInsets = WindowInsets(0.dp),
                    topBar = {
                        DashboardTopBar(
                            enlace = EnlaceAdaptador.CONECTADO,
                            voltaje = 14.1,
                            selectorVehiculo = { SelectorVehiculo(benelli, onClick = {}) },
                            onAdaptador = {},
                            onModoPista = {},
                            onAjustes = {},
                        )
                    },
                    containerColor = RevScopeColors.Background,
                ) { innerPadding ->
                    DashboardContent(
                        estado = estado,
                        acciones = AccionesConducir(),
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                    )
                }
            }
        }
    }
}
