package com.revscope.feature.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captura en inglés del garaje para el README (docs/screenshots/en/garage.png): los perfiles
 * guardados con varios vehículos, la Benelli de SemanticaVehiculoTest activa.
 *
 * Este módulo no aplica el plugin de Roborazzi, así que `recordRoborazziDebug` no lo alcanza: la
 * captura se graba con `REVSCOPE_README_RECORD=1` en el entorno, o si el PNG aún no existe
 * (`gradlew.bat :feature:vehicle:testDebugUnitTest --tests "*CapturasReadme*"`); si ya existe y no
 * hay variable, el test renderiza y no escribe nada.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasReadmeGarageTest {

    private fun perfil(id: Long, nombre: String, tipo: String, maxRpm: Int, redline: Int, combustible: String = "CORRIENTE") =
        VehicleProfileEntity(
            id = id,
            name = nombre,
            type = tipo,
            vin = null,
            enabledPids = "0C,0D,05",
            gearRatios = null,
            createdAt = 0,
            maxRpm = maxRpm,
            redlineRpm = redline,
            fuelType = combustible,
        )

    private val perfiles = listOf(
        perfil(3, "Benelli TNT 150i", "MOTORCYCLE", 12_000, 10_000).copy(adapterAddress = "00:1D:A5:68:98:8B", plate = "NZO28H"),
        perfil(1, "Mazda CX-30 GT", "CAR", 8_000, 6_500, "EXTRA").copy(vin = "3MVDM2W7XNL123456", adapterAddress = "00:1D:A5:02:44:1C"),
        perfil(2, "Renault Kardian", "CAR", 7_000, 6_000),
        perfil(4, "Nissan March", "CAR", 7_000, 6_200),
        perfil(5, "TVS Apache 160 4V FI", "MOTORCYCLE", 11_000, 9_500),
    )

    private val archivo = "../../docs/screenshots/en/garage.png"

    // Sin plugin no hay recordRoborazziDebug: se graba si se pide por entorno o si la imagen aún no existe.
    private val tarea: RoborazziTaskType =
        if (System.getenv("REVSCOPE_README_RECORD") == "1" || !java.io.File(archivo).exists()) {
            RoborazziTaskType.Record
        } else {
            roborazziSystemPropertyTaskType()
        }

    @Test
    fun garage() {
        RuntimeEnvironment.setQualifiers("en-w412dp-h915dp-xxhdpi")
        RuntimeEnvironment.setFontScale(1.0f)
        captureRoboImage(
            filePath = archivo,
            roborazziOptions = RoborazziOptions(taskType = tarea),
        ) {
            RevScopeTheme {
                Box(Modifier.fillMaxSize().background(RevScopeColors.Background).padding(16.dp)) {
                    PerfilesGuardados(
                        perfiles = perfiles,
                        estado = EstadoPerfiles(editandoId = null, activoId = 3, adaptadorConectado = "00:1D:A5:68:98:8B"),
                        onEditar = {},
                        onActivar = {},
                        onBorrar = {},
                    )
                }
            }
        }
    }
}
