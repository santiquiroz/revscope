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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.common.i18n.AndroidStringResolver
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.escucha.EstadoCapturaRuta
import com.revscope.feature.workshop.R
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
        BarraConVolver(titulo = stringResource(R.string.workshop_can_capture_title), onVolver = acciones.onVolver)
        CuerpoDesplazable {
            CampoEtiqueta(estado, acciones.onEtiqueta)
            (estado.estado as? EstadoCapturaRuta.Capturando)?.let { TarjetaEnCurso(it) }
            Resultado(estado.estado)
            Seccion(stringResource(R.string.workshop_can_capture_before_you_go))
            Avisos(estado)
        }
        PieConAccion(
            stringResource(estado.textoBoton),
            if (estado.capturando) acciones.onDetener else acciones.onIniciar,
            habilitado = estado.botonHabilitado,
            aviso = if (estado.botonHabilitado) null else stringResource(R.string.workshop_can_capture_connect_adapter),
        )
    }
}

@Composable
private fun CampoEtiqueta(estado: CapturaRutaUi, onEtiqueta: (String) -> Unit) {
    OutlinedTextField(
        value = estado.etiqueta,
        onValueChange = onEtiqueta,
        enabled = !estado.capturando,
        label = { Text(stringResource(R.string.workshop_can_capture_label), style = RevScopeType.body) },
        supportingText = {
            Text(stringResource(R.string.workshop_can_capture_label_hint), style = RevScopeType.bodySmall)
        },
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
    val resolver = rememberResolver()
    TarjetaTaller {
        Text(
            stringResource(R.string.workshop_can_capture_in_progress, c.etiqueta),
            color = RevScopeColors.Accent,
            style = RevScopeType.label,
        )
        Text(
            stringResource(
                R.string.workshop_can_capture_elapsed,
                FormatoCapturaRuta.cronometro(ahora - c.inicioEpochMs),
                FormatoCapturaRuta.tope,
            ),
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.numeros,
        )
        Dato(stringResource(R.string.workshop_can_capture_frames), c.tramas.toString())
        Dato(stringResource(R.string.workshop_can_capture_overruns), c.desbordes.toString())
        Dato(
            stringResource(R.string.workshop_can_capture_gps_speed),
            FormatoCapturaRuta.velocidad(c.velocidadGpsKmh, resolver),
        )
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
        is EstadoCapturaRuta.Terminada -> CapturaGuardada(estado)
        is EstadoCapturaRuta.Fallo -> TarjetaTaller(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(R.string.workshop_can_capture_failed), color = RevScopeColors.Danger, style = RevScopeType.label)
            Text(estado.mensaje, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        }
        else -> Unit
    }
}

@Composable
private fun CapturaGuardada(estado: EstadoCapturaRuta.Terminada) {
    val resolver = rememberResolver()
    TarjetaTaller(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(R.string.workshop_can_capture_saved), color = RevScopeColors.Success, style = RevScopeType.label)
        Text(estado.id, color = RevScopeColors.TextPrimary, style = RevScopeType.body, fontFamily = FontFamily.Monospace)
        Text(
            FormatoCapturaRuta.resumenTerminada(estado, resolver),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        Text(
            stringResource(R.string.workshop_can_capture_read_from_pc),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
    }
}

@Composable
private fun Avisos(estado: CapturaRutaUi) {
    Aviso(stringResource(R.string.workshop_can_capture_notice_no_trip))
    Aviso(stringResource(R.string.workshop_can_capture_notice_auto_stop, FormatoCapturaRuta.tope))
    Aviso(stringResource(R.string.workshop_can_capture_notice_gps_privacy))
    if (!estado.permisoGps) {
        Aviso(stringResource(R.string.workshop_can_capture_notice_no_location))
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(texto, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
}

@Composable
private fun rememberResolver(): StringResolver {
    val context = LocalContext.current
    return remember(context) { AndroidStringResolver(context) }
}
