package com.revscope.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.NivelBadge
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.obd.workshop.DiagnosticRules
import java.text.SimpleDateFormat
import java.util.Date

internal data class AccionesChequeo(
    val onVolver: () -> Unit = {},
    val onEscanear: () -> Unit = {},
    val onExportarCsv: () -> Unit = {},
    val onCompartir: () -> Unit = {},
)

@Composable
fun HealthCheckScreen(
    onNavigateBack: () -> Unit,
    viewModel: HealthCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    HealthCheckContent(
        state = state,
        acciones = AccionesChequeo(
            onVolver = onNavigateBack,
            onEscanear = viewModel::runHealthCheck,
            onExportarCsv = { viewModel.exportCsv(context) },
            onCompartir = { viewModel.share(context) },
        ),
    )
}

@Composable
internal fun HealthCheckContent(state: HealthCheckViewModel.UiState, acciones: AccionesChequeo) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = stringResource(R.string.workshop_health_check_title), onVolver = acciones.onVolver) {
            if (state is HealthCheckViewModel.UiState.Done) AccionesInforme(acciones)
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "escanear") { BotonEscanear(state, acciones.onEscanear) }
            contenidoChequeo(state, acciones.onEscanear)
        }
    }
}

@Composable
private fun AccionesInforme(acciones: AccionesChequeo) {
    IconButton(onClick = acciones.onExportarCsv) {
        Icon(Icons.Default.Download, stringResource(R.string.workshop_health_check_export), tint = RevScopeColors.Accent)
    }
    IconButton(onClick = acciones.onCompartir) {
        Icon(Icons.Default.Share, stringResource(R.string.workshop_health_check_share), tint = RevScopeColors.Accent)
    }
}

@Composable
private fun BotonEscanear(state: HealthCheckViewModel.UiState, onEscanear: () -> Unit) {
    val corriendo = state is HealthCheckViewModel.UiState.Running
    Button(
        onClick = onEscanear,
        enabled = !corriendo,
        colors = ButtonDefaults.buttonColors(
            containerColor = RevScopeColors.Accent,
            contentColor = RevScopeColors.Background,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(
            stringResource(if (corriendo) R.string.workshop_health_check_scanning else R.string.workshop_health_check_scan_now),
            style = RevScopeType.label,
            textAlign = TextAlign.Center,
        )
    }
}

private fun LazyListScope.contenidoChequeo(state: HealthCheckViewModel.UiState, onReintentar: () -> Unit) {
    when (state) {
        is HealthCheckViewModel.UiState.Idle -> item(key = "intro") { Introduccion() }
        is HealthCheckViewModel.UiState.Running -> item(key = "progreso") { RunningIndicator(state.paso) }
        is HealthCheckViewModel.UiState.Error -> item(key = "error") { ErrorState(state.mensaje, onReintentar) }
        is HealthCheckViewModel.UiState.Done -> resultados(state)
    }
}

@Composable
private fun Introduccion() {
    Text(
        stringResource(R.string.workshop_health_check_intro),
        color = RevScopeColors.TextSecondary,
        style = RevScopeType.body,
    )
}

@Composable
private fun RunningIndicator(paso: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = RevScopeColors.Accent)
        Text(
            paso,
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private fun LazyListScope.resultados(done: HealthCheckViewModel.UiState.Done) {
    item(key = "fecha") {
        val locale = LocalConfiguration.current.locales[0]
        val fecha = SimpleDateFormat("d MMM yyyy, HH:mm", locale).format(Date(done.timestamp)) // i18n-ignore
        Text(stringResource(R.string.workshop_health_check_last, fecha), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
    items(done.items) { item -> DiagnosisRow(item) }
    items(done.noDisponibles) { parametro -> NoDisponibleRow(parametro) }
}

@Composable
private fun NoDisponibleRow(parametro: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NivelBadge(NivelEstado.SIN_DATO)
            Text(parametro, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            Text(stringResource(R.string.workshop_health_check_unavailable_ecu), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
    }
}

@Composable
private fun DiagnosisRow(d: DiagnosticRules.Diagnosis) {
    Surface(shape = RoundedCornerShape(12.dp), color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NivelBadge(nivelDiagnostico(d.nivel))
            Text(d.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            Text(d.causaProbable, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            Text(d.area, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
    }
}
