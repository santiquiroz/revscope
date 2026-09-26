package com.revscope.core.uitesting

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeTheme
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

enum class DispositivoCaptura(val anchoDp: Int, val altoDp: Int, val densidad: String) {
    PEQUENO(360, 640, "xhdpi"),
    GRANDE(412, 915, "xxhdpi");

    val sufijo: String get() = "w$anchoDp"

    fun calificadores(altoMinimoDp: Int = 0): String =
        "w${anchoDp}dp-h${maxOf(altoDp, altoMinimoDp)}dp-$densidad"
}

data class VarianteCaptura(val dispositivo: DispositivoCaptura, val escalaLetra: Float) {
    val sufijo: String get() = "${dispositivo.sufijo}_fs${(escalaLetra * 100).toInt()}"
}

val ESCALAS_LETRA = listOf(1.0f, 1.3f, 2.0f)

fun variantesCaptura(): List<VarianteCaptura> =
    DispositivoCaptura.entries.flatMap { dispositivo ->
        ESCALAS_LETRA.map { escala -> VarianteCaptura(dispositivo, escala) }
    }

fun rutaCaptura(componente: String, variante: VarianteCaptura): String =
    "${MatrizCaptura.DIRECTORIO}/${componente}_${variante.sufijo}.png"

/**
 * Renderiza un composable en los dos teléfonos y las tres escalas de letra de la matriz, con el
 * tema RevScope. El test que la usa corre con Robolectric: `@RunWith(RobolectricTestRunner::class)`,
 * `@GraphicsMode(GraphicsMode.Mode.NATIVE)` y `@Config(sdk = [34])`.
 */
object MatrizCaptura {
    const val DIRECTORIO = "src/test/screenshots"

    /**
     * [altoMinimoDp] alarga la ventana para ver entero un contenido que en el teléfono se recorre
     * con scroll; el ancho y la densidad siguen siendo los del teléfono de la variante.
     */
    fun componente(nombre: String, altoMinimoDp: Int = 0, contenido: @Composable () -> Unit) =
        capturarVariantes(nombre, altoMinimoDp) { EnvolturaComponente(contenido) }

    fun pantalla(nombre: String, altoMinimoDp: Int = 0, contenido: @Composable () -> Unit) =
        capturarVariantes(nombre, altoMinimoDp) { EnvolturaPantalla(contenido) }

    /** Los diálogos viven en su propia ventana: se captura la pantalla entera, no solo la vista. */
    fun dialogo(nombre: String, contenido: @Composable () -> Unit) {
        variantesCaptura().forEach { variante ->
            aplicarVariante(variante, altoMinimoDp = 0)
            capturarPantallaConVentanas(rutaCaptura(nombre, variante)) { EnvolturaPantalla(contenido) }
        }
    }

    private fun capturarPantallaConVentanas(ruta: String, contenido: @Composable () -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { escenario ->
            escenario.onActivity { it.setContent { contenido() } }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            captureScreenRoboImage(filePath = ruta)
        }
    }

    private fun capturarVariantes(nombre: String, altoMinimoDp: Int, envoltura: @Composable () -> Unit) {
        variantesCaptura().forEach { variante ->
            aplicarVariante(variante, altoMinimoDp)
            captureRoboImage(filePath = rutaCaptura(nombre, variante)) { envoltura() }
        }
    }

    private fun aplicarVariante(variante: VarianteCaptura, altoMinimoDp: Int) {
        RuntimeEnvironment.setQualifiers(variante.dispositivo.calificadores(altoMinimoDp))
        RuntimeEnvironment.setFontScale(variante.escalaLetra)
    }
}

@Composable
private fun EnvolturaComponente(contenido: @Composable () -> Unit) {
    RevScopeTheme {
        Box(
            Modifier
                .fillMaxWidth()
                .background(RevScopeColors.Background)
                .padding(16.dp),
        ) { contenido() }
    }
}

@Composable
private fun EnvolturaPantalla(contenido: @Composable () -> Unit) {
    RevScopeTheme {
        Box(Modifier.fillMaxSize().background(RevScopeColors.Background)) { contenido() }
    }
}
