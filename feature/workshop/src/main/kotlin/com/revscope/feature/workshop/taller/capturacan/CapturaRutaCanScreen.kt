package com.revscope.feature.workshop.taller.capturacan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.escucha.EstadoCapturaRuta
import com.revscope.feature.workshop.taller.TarjetaTaller
import com.revscope.feature.workshop.taller.prueba.CuerpoDesplazable
import com.revscope.feature.workshop.taller.prueba.PieConAccion
import com.revscope.feature.workshop.taller.prueba.Seccion
import kotlinx.coroutines.delay

data class AccionesCapturaRuta(
    val onVolver: () -> Unit = {},
    val onEtiqueta: (String) -> Unit = {},
    val onIniciar: () -> Unit = {},
    val onDetener: () -> Unit = {},
)

@Composable
fun CapturaRutaCanScreen(onVolver: () -> Unit, viewModel: CapturaRutaCanViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsState()
    CapturaRutaCanContent(
        estado,
        AccionesCapturaRuta(onVolver, viewModel::cambiarEtiqueta, viewModel::iniciar, viewModel::detener),
    )
}

@Composable
fun CapturaRutaCanContent(estado: CapturaRutaUi, acciones: AccionesCapturaRuta) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = "Captura CAN de ruta", onVolver = acciones.onVolver)
        CuerpoDesplazable {
            CampoEtiqueta(estado, acciones.onEtiqueta)
            (estado.estado as? EstadoCapturaRuta.Capturando)?.let { TarjetaEnCurso(it) }
            Resultado(estado.estado)
            Seccion("Antes de salir")
            Avisos(estado)
        }
        PieConAccion(
            estado.textoBoton,
            if (estado.capturando) acciones.onDetener else acciones.onIniciar,
            habilitado = estado.botonHabilitado,
            aviso = if (estado.botonHabilitado) null else "Conecta el adaptador para capturar el bus",
        )
    }
}

@Composable
private fun CampoEtiqueta(estado: CapturaRutaUi, onEtiqueta: (String) -> Unit) {
    OutlinedTextField(
        value = estado.etiqueta,
        onValueChange = onEtiqueta,
        enabled = !estado.capturando,
        label = { Text("Etiqueta", style = RevScopeType.body) },
        supportingText = { Text("Va en el nombre de la captura, p. ej. «ruta_autopista»", style = RevScopeType.bodySmall) },
        singleLine = true,
        textStyle = RevScopeType.body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = RevScopeColors.TextPrimary,
            unfocusedTextColor = RevScopeColors.TextPrimary,
            disabledTextColor = RevScopeColors.TextSecondary,
            focusedBorderColor = RevScopeColors.Accent,
            unfocusedBorderColor = RevScopeColors.TextMuted,
            focusedLabelColor = RevScopeColors.Accent,
            unfocusedLabelColor = RevScopeColors.TextSecondary,
            cursorColor = RevScopeColors.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TarjetaEnCurso(c: EstadoCapturaRuta.Capturando) {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // El estado solo cambia con tramas nuevas: el cronómetro necesita su propio pulso.
    LaunchedEffect(c.inicioEpochMs) {
        while (true) {
            ahora = System.currentTimeMillis()
            delay(1_000)
        }
    }
    TarjetaTaller {
        Text("CAPTURANDO · ${c.etiqueta}", color = RevScopeColors.Accent, style = RevScopeType.label)
        Text(
            "${FormatoCapturaRuta.cronometro(ahora - c.inicioEpochMs)} de ${FormatoCapturaRuta.tope}",
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.numeros,
        )
        Dato("Tramas", c.tramas.toString())
        Dato("Desbordes del adaptador", c.desbordes.toString())
        Dato("Velocidad GPS", FormatoCapturaRuta.velocidad(c.velocidadGpsKmh))
    }
}

@Composable
private fun Dato(nombre: String, valor: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(nombre, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        Text(valor, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
    }
}

@Composable
private fun Resultado(estado: EstadoCapturaRuta) {
    when (estado) {
        is EstadoCapturaRuta.Terminada -> TarjetaTaller(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text("CAPTURA GUARDADA", color = RevScopeColors.Success, style = RevScopeType.label)
            Text(estado.id, color = RevScopeColors.TextPrimary, style = RevScopeType.body, fontFamily = FontFamily.Monospace)
            Text(FormatoCapturaRuta.resumenTerminada(estado), color = RevScopeColors.TextSecondary, style = RevScopeType.body)
            Text(
                "Léela desde el PC con leer_captura_can y leer_gps_captura_can.",
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
        }
        is EstadoCapturaRuta.Fallo -> TarjetaTaller(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text("NO SE PUDO CAPTURAR", color = RevScopeColors.Danger, style = RevScopeType.label)
            Text(estado.mensaje, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        }
        else -> Unit
    }
}

@Composable
private fun Avisos(estado: CapturaRutaUi) {
    Aviso("No se graba un viaje mientras capturas: el adaptador queda escuchando el bus y no sondea PIDs.")
    Aviso("Se detiene sola a los ${FormatoCapturaRuta.tope} min. Sigue con la pantalla apagada mientras la app esté conectada.")
    Aviso("Del GPS solo se guardan velocidad, precisión y rumbo, una vez por segundo; nunca la posición.")
    if (!estado.permisoGps) {
        Aviso("Sin permiso de ubicación: la captura no tendrá velocidad GPS.")
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(texto, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
}
