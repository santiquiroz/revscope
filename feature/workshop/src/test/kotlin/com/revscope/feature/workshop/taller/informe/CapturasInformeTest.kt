package com.revscope.feature.workshop.taller.informe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasInformeTest {

    @Test
    fun informeBenelli() = MatrizCaptura.pantalla("InformeContent_benelli") {
        InformeContent(
            estado = estadoBenelli(),
            acciones = AccionesInforme(onVolver = {}),
            vistaPrevia = { modifier -> VistaPreviaInformeFalsa(modifier) },
        )
    }

    private fun estadoBenelli() = InformeUi(
        cargando = false,
        titulo = "Informe de taller",
        html = "<html><body>Informe estable para la captura</body></html>",
        interpretacion = "La señal permanece baja durante todo el recorrido.",
    )
}

@Composable
private fun VistaPreviaInformeFalsa(modifier: Modifier) {
    Column(
        modifier = modifier
            .background(RevScopeColors.SurfaceHigh)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Surface(color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Text("SEÑAL DEL TPS FUERA DE RANGO", color = RevScopeColors.Accent, style = RevScopeType.label)
                Text("Benelli TNT 150i · P0122", color = RevScopeColors.TextPrimary, style = RevScopeType.title)
                Text("25 sep 2026 · Diagnóstico de taller", color = RevScopeColors.TextSecondary, style = RevScopeType.body)
                HorizontalDivider(color = RevScopeColors.TextMuted)
                SeccionPrevia(
                    titulo = "Conclusión",
                    contenido = "Falla · La señal medida permanece baja, aunque referencia y masa son correctas.",
                )
                SeccionPrevia(
                    titulo = "Medición con multímetro",
                    contenido = "Cerrado 0,12 V · Medio 0,46 V · A fondo 0,89 V",
                )
                SeccionPrevia(
                    titulo = "Lo que registró la ECU",
                    contenido = "2,4 % ≈ 0,12 V · 9,2 % ≈ 0,46 V · 17,8 % ≈ 0,89 V",
                )
                Text(
                    "Valores típicos salvo que se indique fuente.",
                    color = RevScopeColors.TextSecondary,
                    style = RevScopeType.body,
                )
            }
        }
    }
}

@Composable
private fun SeccionPrevia(titulo: String, contenido: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        Text(contenido, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
    }
}
