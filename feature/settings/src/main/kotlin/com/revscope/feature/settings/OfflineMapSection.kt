package com.revscope.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.maps.MapDownloadState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)
private val DangerColor = Color(0xFFFF3D5A)

private const val OFFLINE_MAP_SIZE_LABEL = "~913 MB"

/**
 * Card "Mapa offline de Colombia" en Ajustes → Mapa: estado desde [SettingsViewModel.mapDownloadState],
 * descarga/cancela/borra a través del ViewModel. Muestra el snackbar "Mapa offline listo" al
 * completar una descarga en curso (transición Downloading → Idle con exists=true).
 */
@Composable
fun OfflineMapSection(vm: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val downloadState by vm.mapDownloadState.collectAsState()
    var wasDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(downloadState) {
        val current = downloadState
        if (wasDownloading && current is MapDownloadState.Idle && current.exists) {
            snackbarHostState.showSnackbar("Mapa offline listo")
        }
        wasDownloading = current is MapDownloadState.Downloading
    }

    OfflineMapCard(
        downloadState = downloadState,
        isOnWifi = vm::isOnWifiNow,
        onDownload = vm::downloadOfflineMap,
        onCancel = vm::cancelOfflineMapDownload,
        onDelete = vm::deleteOfflineMap,
    )
}

@Composable
private fun OfflineMapCard(
    downloadState: MapDownloadState,
    isOnWifi: () -> Boolean,
    onDownload: (allowCellular: Boolean) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDownloadConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Mapa offline de Colombia ($OFFLINE_MAP_SIZE_LABEL)",
            color = TextPrimaryColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        OfflineMapStateContent(
            downloadState = downloadState,
            onDownloadClick = { showDownloadConfirm = true },
            onCancelClick = onCancel,
            onDeleteClick = { showDeleteConfirm = true },
        )
    }

    if (showDownloadConfirm) {
        DownloadConfirmDialog(
            isOnWifi = isOnWifi(),
            onConfirm = { allowCellular ->
                showDownloadConfirm = false
                onDownload(allowCellular)
            },
            onDismiss = { showDownloadConfirm = false },
        )
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun OfflineMapStateContent(
    downloadState: MapDownloadState,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    when (downloadState) {
        is MapDownloadState.Idle -> IdleContent(downloadState, onDownloadClick, onDeleteClick)
        is MapDownloadState.Downloading -> DownloadingContent(downloadState, onCancelClick)
        is MapDownloadState.Error -> ErrorContent(downloadState, onDownloadClick)
    }
}

@Composable
private fun IdleContent(state: MapDownloadState.Idle, onDownloadClick: () -> Unit, onDeleteClick: () -> Unit) {
    if (state.exists) {
        Text(
            "Descargado · ${formatGigabytes(state.sizeBytes)} · ${formatDownloadedAt(state.downloadedAtMs)}",
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        Button(
            onClick = onDeleteClick,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
        ) { Text("Borrar", color = TextPrimaryColor) }
    } else {
        Text("No descargado", color = TextMutedColor, fontSize = 12.sp)
        Button(
            onClick = onDownloadClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text("Descargar", color = BgColor) }
    }
}

@Composable
private fun DownloadingContent(state: MapDownloadState.Downloading, onCancelClick: () -> Unit) {
    val percent = (state.progress * 100).toInt()
    LinearProgressIndicator(
        progress = { state.progress },
        modifier = Modifier.fillMaxWidth(),
        color = AccentColor,
        trackColor = SurfaceHighColor,
    )
    Text("Descargando… $percent%", color = TextMutedColor, fontSize = 12.sp)
    Button(
        onClick = onCancelClick,
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
    ) { Text("Cancelar", color = TextPrimaryColor) }
}

@Composable
private fun ErrorContent(state: MapDownloadState.Error, onRetryClick: () -> Unit) {
    Text(state.message, color = DangerColor, fontSize = 12.sp)
    Button(
        onClick = onRetryClick,
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
    ) { Text("Reintentar", color = BgColor) }
}

@Composable
private fun DownloadConfirmDialog(
    isOnWifi: Boolean,
    onConfirm: (allowCellular: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = {
            Text(
                if (isOnWifi) "Descargar mapa offline" else "Sin WiFi",
                color = TextPrimaryColor,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                if (isOnWifi) {
                    "Se descargará el mapa offline de Colombia ($OFFLINE_MAP_SIZE_LABEL)."
                } else {
                    "Sin WiFi — ¿usar datos móviles? ($OFFLINE_MAP_SIZE_LABEL)"
                },
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(!isOnWifi) }) {
                Text(if (isOnWifi) "Descargar" else "Usar datos móviles", color = AccentColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMutedColor) }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text("Borrar mapa offline", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "Se eliminará el mapa descargado. Podrás volver a descargarlo cuando quieras.",
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Borrar", color = AccentColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMutedColor) }
        },
    )
}

private fun formatGigabytes(sizeBytes: Long): String =
    String.format(Locale.US, "%.1f GB", sizeBytes / 1_000_000_000.0)

private val downloadedAtFormat = SimpleDateFormat("dd MMM yyyy", Locale("es"))

private fun formatDownloadedAt(downloadedAtMs: Long?): String =
    downloadedAtMs?.let { downloadedAtFormat.format(Date(it)) } ?: "fecha desconocida"
