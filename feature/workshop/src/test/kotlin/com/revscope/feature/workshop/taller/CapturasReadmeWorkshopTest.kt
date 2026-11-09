package com.revscope.feature.workshop.taller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.designsystem.SelectorVehiculo
import com.revscope.core.designsystem.VehiculoEnEncabezado
import com.revscope.core.obd.taller.pid.CapacidadesEcu
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captura en inglés del inicio de Taller para el README (docs/screenshots/en/workshop.png): la
 * Benelli de CasoBenelli conectada, sin sesión abierta, con sesiones anteriores tituladas en
 * inglés (el título lo escribe el usuario).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasReadmeWorkshopTest {

    private val resolver = XmlStringResolver.en(
        com.revscope.core.obd.R::class.java,
        com.revscope.feature.workshop.R::class.java,
        com.revscope.core.designsystem.R::class.java,
        resDirs = listOf("src/main/res", "../../core/obd/src/main/res", "../../core/designsystem/src/main/res"),
    )

    private val selector = VehiculoEnEncabezado(
        "Benelli TNT 150i",
        esMoto = true,
        estadoEnlace = "vLinker FS",
        colorEstado = RevScopeColors.Success,
    )

    private val dia = 86_400_000L
    private val minuto = 60_000L
    private val julio11 = 1_783_810_800_000L

    // Meses elegidos para que FechasTaller (meses abreviados en español) coincida con el inglés.
    private val anteriores = listOf(
        SesionTaller(id = 9, vehiculoId = BENELLI.id, inicio = julio11, cierre = julio11 + 50 * minuto, titulo = "Routine check"),
        SesionTaller(id = 7, vehiculoId = BENELLI.id, inicio = julio11 - 40 * dia, cierre = julio11 - 40 * dia + 30 * minuto, titulo = "Battery keeps draining"),
        SesionTaller(id = 5, vehiculoId = BENELLI.id, inicio = julio11 - 130 * dia, cierre = julio11 - 130 * dia + 20 * minuto, titulo = "Check engine light on"),
        SesionTaller(id = 2, vehiculoId = BENELLI.id, inicio = julio11 - 132 * dia, cierre = julio11 - 132 * dia + 20 * minuto, titulo = "Losing power"),
    )

    private val capacidades = CapacidadesEcu.desde(
        atDpn = "A5",
        pidsAnunciados = listOf("04", "05", "06", "07", "0B", "0C", "0D", "0E", "0F", "11", "1C", "42"),
        resolver = resolver,
        tasaMedidaHz = 4.8,
    )

    @Test
    fun workshop() {
        val estado = ResumenHub.de(
            DatosHub(BENELLI, anteriores, emptyList()),
            CasoBenelli.conectado,
            verTodas = false,
            zona = BOGOTA,
            capacidades = capacidades,
        )
        RuntimeEnvironment.setQualifiers("en-w412dp-h2000dp-xxhdpi")
        RuntimeEnvironment.setFontScale(1.0f)
        captureRoboImage(filePath = "../../docs/screenshots/en/workshop.png") {
            RevScopeTheme {
                Box(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
                    TallerHubContent(estado, AccionesHub()) { SelectorVehiculo(selector, onClick = {}) }
                }
            }
        }
    }
}
