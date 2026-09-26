package com.revscope.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.unit.dp

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasSistemaDisenoTest {

    @Test
    fun selectorVehiculo() = MatrizCaptura.componente("SelectorVehiculo") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorVehiculo(
                VehiculoEnEncabezado("Benelli TNT 150i", esMoto = true, "Android-Vlink", RevScopeColors.Success),
                onClick = {},
            )
            SelectorVehiculo(
                VehiculoEnEncabezado(
                    "Mazda CX-30 GT Grand Touring 2.5 Skyactiv-G automática",
                    esMoto = false,
                    "Sin conexión",
                    RevScopeColors.TextMuted,
                ),
                onClick = {},
            )
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun chipSeleccion() = MatrizCaptura.componente("ChipSeleccion") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipSeleccion("Pedal y mariposa (49, 4A, 11)", seleccionado = true, onClick = {})
            ChipSeleccion("0C RPM", seleccionado = false, onClick = {})
            ChipSeleccion("7E0 → 7E8", seleccionado = false, onClick = {}, monoespaciado = true)
        }
    }

    @Test
    fun avisoDescartable() = MatrizCaptura.componente("AvisoDescartable") {
        AvisoDescartable(
            texto = "Limitada a 10 Hz para cuidar la batería y la temperatura del teléfono",
            onDescartar = {},
        )
    }

    @Test
    fun filaEtiquetaValor() = MatrizCaptura.componente("FilaEtiquetaValor") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilaEtiquetaValor("Presión de combustible del riel", "412,5 kPa")
            FilaEtiquetaValor("Ajuste largo de combustible (banco 1)", "+2,3 %")
        }
    }

    @Test
    fun nivelBadge() = MatrizCaptura.componente("NivelBadge") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NivelEstado.entries.forEach { NivelBadge(it) }
            NivelBadge(NivelEstado.ATENCION, texto = "Vence en 12 días")
        }
    }

    @Test
    fun emptyState() = MatrizCaptura.componente("EmptyState") {
        EmptyState(
            mensaje = "Todavía no hay viajes guardados. Conecta el adaptador o inicia un viaje GPS desde Conducir.",
            accion = AccionEstado("Iniciar un viaje") {},
        )
    }

    @Test
    fun errorState() = MatrizCaptura.componente("ErrorState") {
        ErrorState(mensaje = "No se pudo leer la ECU: el adaptador no respondió a tiempo", onReintentar = {})
    }

    @Test
    fun barraConVolver() = MatrizCaptura.componente("BarraConVolver") {
        BarraConVolver(
            titulo = "Códigos de falla y freeze frame",
            subtitulo = "Benelli TNT 150i",
            onVolver = {},
        )
    }

    @Test
    fun indicadorPasos() = MatrizCaptura.componente("IndicadorPasos") {
        IndicadorPasos(actual = 2, total = 4, titulo = "Acelerador a medio recorrido")
    }

    @Test
    fun confirmarDestructivoDialog() = MatrizCaptura.dialogo("ConfirmarDestructivoDialog") {
        ConfirmarDestructivoDialog(
            titulo = "¿Borrar el viaje?",
            mensaje = "Viaje del 25 sep 2026, 18:40 (12,3 km).",
            avisos = listOf("Se borran la telemetría y la ruta. No se puede deshacer."),
            textoConfirmar = "Borrar viaje",
            onConfirmar = {},
            onCancelar = {},
        )
    }
}
