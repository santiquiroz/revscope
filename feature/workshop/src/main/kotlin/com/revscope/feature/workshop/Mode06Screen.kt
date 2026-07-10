package com.revscope.feature.workshop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.protocol.Mode06Parser

private val AccentColor = Color(0xFFE8FF00)
private val SurfaceColor = Color(0xFF12121A)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)
private val SuccessColor = Color(0xFF3DFF8E)
private val DangerColor = Color(0xFFFF3D5A)

@Composable
fun Mode06Screen(
    onNavigateBack: () -> Unit,
    viewModel: Mode06ViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val state by viewModel.state.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val context = LocalContext.current
    val doneState = state as? Mode06ViewModel.UiState.Done

    LaunchedEffect(isConnected) {
        if (isConnected && state is Mode06ViewModel.UiState.Idle) viewModel.runScan()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextColor)
            }
            Text(
                "Resultados a bordo (Mode 06)",
                color = TextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { viewModel.exportResults(context) },
                enabled = !doneState?.groups.isNullOrEmpty(),
            ) {
                Icon(Icons.Default.Download, contentDescription = "Exportar CSV", tint = AccentColor)
            }
        }
        Text(
            "Los valores dependen del fabricante — útil para comparar antes/después de una reparación.",
            color = TextMutedColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (!isConnected) {
            Text(
                "Conecta el adaptador para leer los resultados de las pruebas a bordo.",
                color = DangerColor,
                fontSize = 13.sp,
            )
            return@Column
        }

        Mode06Body(state, onRetry = viewModel::runScan)
    }
}

@Composable
private fun Mode06Body(state: Mode06ViewModel.UiState, onRetry: () -> Unit) {
    when (state) {
        Mode06ViewModel.UiState.Idle -> Text("Preparando…", color = TextMutedColor, fontSize = 13.sp)
        is Mode06ViewModel.UiState.Scanning -> ScanningProgress(state)
        is Mode06ViewModel.UiState.Done -> ResultsList(state.groups, state.incomplete, onRetry)
        is Mode06ViewModel.UiState.Error -> ErrorState(state.message, onRetry)
    }
}

@Composable
private fun ScanningProgress(state: Mode06ViewModel.UiState.Scanning) {
    val progress = if (state.total > 0) state.current.toFloat() / state.total else 0f
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = AccentColor,
        trackColor = SurfaceColor,
    )
    Text(
        "Leyendo pruebas a bordo (${state.current}/${state.total})…",
        color = TextMutedColor,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Text(message, color = DangerColor, fontSize = 13.sp)
    Button(
        onClick = onRetry,
        modifier = Modifier.padding(top = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
    ) {
        Text("Reintentar", color = SurfaceColor)
    }
}

@Composable
private fun ResultsList(groups: List<Mode06ViewModel.MidGroup>, incomplete: Boolean, onRetry: () -> Unit) {
    Column {
        if (incomplete) IncompleteScanBanner()
        if (groups.isEmpty()) {
            ErrorState("El vehículo no devolvió resultados Mode 06 legibles.", onRetry)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups, key = { it.mid }) { group -> MidGroupCard(group) }
            }
        }
    }
}

@Composable
private fun IncompleteScanBanner() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DangerColor.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Text(
            "Escaneo incompleto — se perdió el enlace; repite el escaneo",
            color = DangerColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun MidGroupCard(group: Mode06ViewModel.MidGroup) {
    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${group.name} (MID ${group.mid})",
                color = TextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            group.results.forEach { result -> TestResultRow(result) }
        }
    }
}

@Composable
private fun TestResultRow(result: Mode06Parser.TestResult) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (result.pass) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = if (result.pass) "Dentro de límites" else "Fuera de límites",
            tint = if (result.pass) SuccessColor else DangerColor,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "TID ${result.tid} · UAS 0x${"%02X".format(result.uasId)}",
                color = TextMutedColor,
                fontSize = 11.sp,
            )
            Text(
                "%.2f %s".format(result.value, result.unit),
                color = TextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "límite %.2f–%.2f %s · raw %d (%d–%d)".format(
                    result.min, result.max, result.unit,
                    result.rawValue, result.rawMin, result.rawMax,
                ),
                color = TextMutedColor,
                fontSize = 11.sp,
            )
        }
    }
}
