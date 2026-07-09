package com.revscope.feature.workshop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.obd.legal.DocumentStatusCalculator

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)
private val NivelOkColor = Color(0xFF4CAF50)
private val NivelAtencionColor = Color(0xFFFFC107)
private val NivelVencidoColor = Color(0xFFFF5252)

private const val SIMIT_URL = "https://www.fcm.org.co/simit/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlDiaScreen(
    onOpenHealthCheck: () -> Unit,
    onOpenProfiles: () -> Unit,
    vm: AlDiaViewModel = hiltViewModel(),
) {
    val profile by vm.activeProfile.collectAsState()
    val statuses by vm.docStatuses.collectAsState()
    val licenseExpiresAt by vm.licenseExpiresAt.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Text("Vehículo al día", color = TextColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        val activeProfile = profile
        if (activeProfile == null) {
            EmptyState(onOpenProfiles)
            return@Column
        }

        Text(
            activeProfile.name,
            color = TextMutedColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        val soat = statuses.find(DocumentStatusCalculator.DocType.SOAT)
        val rtm = statuses.find(DocumentStatusCalculator.DocType.RTM)
        val picoYPlaca = statuses.find(DocumentStatusCalculator.DocType.PICO_Y_PLACA)
        val todoRiesgo = statuses.find(DocumentStatusCalculator.DocType.TODO_RIESGO)
        val licencia = statuses.find(DocumentStatusCalculator.DocType.LICENCIA)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            soat?.let { item { DocCard(it, onOpenProfiles) } }
            rtm?.let {
                item {
                    DocCard(it, onOpenProfiles) {
                        TextButton(onClick = onOpenHealthCheck, contentPadding = PaddingValues(0.dp)) {
                            Text("Chequeo mecánico", color = AccentColor, fontSize = 12.sp)
                        }
                    }
                }
            }
            picoYPlaca?.let { item { DocCard(it, onOpenProfiles) } }
            item { MultasCard(plate = activeProfile.plate) }
            todoRiesgo?.let { item { DocCard(it, onOpenProfiles) } }
            licencia?.let {
                item { LicenseCard(status = it, expiresAt = licenseExpiresAt, onChange = vm::setLicenseExpiresAt) }
            }
        }
    }
}

private fun List<DocumentStatusCalculator.DocStatus>.find(
    tipo: DocumentStatusCalculator.DocType,
): DocumentStatusCalculator.DocStatus? = firstOrNull { it.tipo == tipo }

private fun nivelColor(nivel: DocumentStatusCalculator.Nivel): Color = when (nivel) {
    DocumentStatusCalculator.Nivel.OK -> NivelOkColor
    DocumentStatusCalculator.Nivel.ATENCION -> NivelAtencionColor
    DocumentStatusCalculator.Nivel.VENCIDO -> NivelVencidoColor
    DocumentStatusCalculator.Nivel.SIN_CONFIGURAR -> TextMutedColor
}

@Composable
private fun DocCard(
    status: DocumentStatusCalculator.DocStatus,
    onClickConfigure: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val sinConfigurar = status.nivel == DocumentStatusCalculator.Nivel.SIN_CONFIGURAR
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = sinConfigurar, onClick = onClickConfigure),
    ) {
        DocCardContent(status, sinConfigurar, extra)
    }
}

@Composable
private fun DocCardContent(
    status: DocumentStatusCalculator.DocStatus,
    sinConfigurar: Boolean,
    extra: (@Composable () -> Unit)?,
) {
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NivelDot(status.nivel)
            Spacer(Modifier.width(8.dp))
            Text(status.titulo, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = status.detalle,
            color = if (sinConfigurar) TextMutedColor else TextColor,
            fontSize = 12.sp,
        )
        if (sinConfigurar) {
            Text(
                "Toca para configurar",
                color = AccentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        extra?.let {
            Spacer(Modifier.height(6.dp))
            it()
        }
    }
}

@Composable
private fun NivelDot(nivel: DocumentStatusCalculator.Nivel) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(10.dp)
            .background(nivelColor(nivel), CircleShape),
    )
}

@Composable
private fun MultasCard(plate: String?) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NivelDot(DocumentStatusCalculator.Nivel.SIN_CONFIGURAR)
                Spacer(Modifier.width(8.dp))
                Text("Multas", color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Consulta comparendos en el sistema SIMIT", color = TextMutedColor, fontSize = 12.sp)
            TextButton(
                onClick = { openSimit(context, plate) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text("Consultar en SIMIT", color = AccentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun openSimit(context: Context, plate: String?) {
    copyPlateToClipboard(context, plate)
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SIMIT_URL)))
    }.onFailure {
        Toast.makeText(context, "No hay navegador disponible", Toast.LENGTH_SHORT).show()
    }
}

private fun copyPlateToClipboard(context: Context, plate: String?) {
    if (plate.isNullOrBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Placa", plate))
    Toast.makeText(context, "Placa copiada", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseCard(
    status: DocumentStatusCalculator.DocStatus,
    expiresAt: Long?,
    onChange: (Long?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
    ) {
        DocCardContent(status, status.nivel == DocumentStatusCalculator.Nivel.SIN_CONFIGURAR) {
            Text("Toca para editar", color = AccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = expiresAt)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(pickerState.selectedDateMillis)
                    showPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun EmptyState(onOpenProfiles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Verified,
            contentDescription = null,
            tint = TextMutedColor,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Sin vehículo activo", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Activa un vehículo en Perfiles para ver el estado de sus documentos",
            color = TextMutedColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenProfiles, colors = ButtonDefaults.buttonColors(containerColor = AccentColor)) {
            Text("Ir a perfiles", color = BgColor, fontWeight = FontWeight.Bold)
        }
    }
}
