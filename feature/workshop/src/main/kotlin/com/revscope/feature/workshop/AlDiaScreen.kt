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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.AccionEstado
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.NivelBadge
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.trip.MaintenanceCalculator

private const val SIMIT_URL = "https://www.fcm.org.co/simit/"

@Composable
fun AlDiaScreen(
    onNavigateBack: () -> Unit,
    onOpenHealthCheck: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenMaintenance: () -> Unit,
    vm: AlDiaViewModel = hiltViewModel(),
) {
    val profile by vm.activeProfile.collectAsState()
    val statuses by vm.docStatuses.collectAsState()
    val licenseExpiresAt by vm.licenseExpiresAt.collectAsState()
    val maintenanceEstados by vm.maintenanceEstados.collectAsState()
    val zoneBrief by vm.zoneBrief.collectAsState()

    AlDiaContent(
        vehiculo = profile?.let { VehiculoAlDia(nombre = it.name, placa = it.plate) },
        estados = statuses,
        licenciaVenceEn = licenseExpiresAt,
        mantenimientoNivel = MaintenanceCalculator.peorNivel(maintenanceEstados),
        mantenimientoDetalle = MaintenanceCalculator.detalleTexto(maintenanceEstados),
        zoneBrief = zoneBrief,
        acciones = AccionesAlDia(
            onVolver = onNavigateBack,
            onChequeo = onOpenHealthCheck,
            onPerfiles = onOpenProfiles,
            onMantenimiento = onOpenMaintenance,
            onCambiarLicencia = vm::setLicenseExpiresAt,
        ),
    )
}

internal data class VehiculoAlDia(val nombre: String, val placa: String?)

internal data class AccionesAlDia(
    val onVolver: () -> Unit = {},
    val onChequeo: () -> Unit = {},
    val onPerfiles: () -> Unit = {},
    val onMantenimiento: () -> Unit = {},
    val onCambiarLicencia: (Long?) -> Unit = {},
)

// Ancho mínimo de tarjeta que crece con la letra: con 200 % la rejilla pasa a una columna
// en vez de partir «Tecnomecánica» a mitad de palabra.
private val ANCHO_MIN_TARJETA = 150.dp

@Composable
internal fun AlDiaContent(
    vehiculo: VehiculoAlDia?,
    estados: List<DocumentStatusCalculator.DocStatus>,
    licenciaVenceEn: Long?,
    mantenimientoNivel: DocumentStatusCalculator.Nivel,
    mantenimientoDetalle: String,
    zoneBrief: com.revscope.core.obd.service.ZoneBriefHolder.ZoneBrief?,
    acciones: AccionesAlDia,
) {
    val escalaLetra = LocalDensity.current.fontScale
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = "Vehículo al día", subtitulo = vehiculo?.nombre, onVolver = acciones.onVolver)
        RejillaAlDia(vehiculo, estados, licenciaVenceEn, mantenimientoNivel, mantenimientoDetalle, zoneBrief, acciones, escalaLetra)
    }
}

@Composable
private fun RejillaAlDia(
    vehiculo: VehiculoAlDia?,
    estados: List<DocumentStatusCalculator.DocStatus>,
    licenciaVenceEn: Long?,
    mantenimientoNivel: DocumentStatusCalculator.Nivel,
    mantenimientoDetalle: String,
    zoneBrief: com.revscope.core.obd.service.ZoneBriefHolder.ZoneBrief?,
    acciones: AccionesAlDia,
    escalaLetra: Float,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = ANCHO_MIN_TARJETA * escalaLetra),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (vehiculo == null) {
            item(span = { GridItemSpan(maxLineSpan) }) { SinVehiculoActivo(acciones.onPerfiles) }
            return@LazyVerticalGrid
        }
        zoneBrief?.let { brief -> item(span = { GridItemSpan(maxLineSpan) }) { ZoneBriefCard(brief) } }
        estados.find(DocumentStatusCalculator.DocType.SOAT)?.let { item { DocCard(it, acciones.onPerfiles) } }
        estados.find(DocumentStatusCalculator.DocType.RTM)?.let {
            item {
                DocCard(it, acciones.onPerfiles) {
                    TextButton(onClick = acciones.onChequeo) {
                        Text("Chequeo mecánico", color = RevScopeColors.Accent, style = RevScopeType.label)
                    }
                }
            }
        }
        estados.find(DocumentStatusCalculator.DocType.PICO_Y_PLACA)?.let { item { DocCard(it, acciones.onPerfiles) } }
        item { MultasCard(plate = vehiculo.placa) }
        estados.find(DocumentStatusCalculator.DocType.TODO_RIESGO)?.let { item { DocCard(it, acciones.onPerfiles) } }
        item {
            MaintenanceCard(nivel = mantenimientoNivel, detalle = mantenimientoDetalle, onClick = acciones.onMantenimiento)
        }
        estados.find(DocumentStatusCalculator.DocType.LICENCIA)?.let {
            item { LicenseCard(status = it, expiresAt = licenciaVenceEn, onChange = acciones.onCambiarLicencia) }
        }
    }
}

/** Brief del compañero de viaje: combustible/peajes/restricciones de la zona actual. */
@Composable
private fun ZoneBriefCard(brief: com.revscope.core.obd.service.ZoneBriefHolder.ZoneBrief) {
    val fuente = when (brief.source) {
        com.revscope.core.obd.service.ZoneBriefHolder.Source.COMMUNITY -> "De la comunidad"
        com.revscope.core.obd.service.ZoneBriefHolder.Source.AI -> "Generado por IA"
    }
    Surface(shape = RoundedCornerShape(14.dp), color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(brief.place, color = RevScopeColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(fuente, color = RevScopeColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Text(brief.body, color = RevScopeColors.TextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

private fun List<DocumentStatusCalculator.DocStatus>.find(
    tipo: DocumentStatusCalculator.DocType,
): DocumentStatusCalculator.DocStatus? = firstOrNull { it.tipo == tipo }

@Composable
private fun DocCard(
    status: DocumentStatusCalculator.DocStatus,
    onClickConfigure: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val sinConfigurar = status.nivel == DocumentStatusCalculator.Nivel.SIN_CONFIGURAR
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RevScopeColors.Surface,
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
        TituloConNivel(status.titulo, nivelDocumento(status.nivel))
        Spacer(Modifier.height(8.dp))
        Text(
            text = status.detalle,
            color = if (sinConfigurar) RevScopeColors.TextSecondary else RevScopeColors.TextPrimary,
            fontSize = 12.sp,
        )
        if (sinConfigurar) {
            Text(
                "Toca para configurar",
                color = RevScopeColors.Accent,
                fontSize = 12.sp,
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
private fun TituloConNivel(titulo: String, nivel: NivelConTexto) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, color = RevScopeColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        NivelBadge(nivel.nivel, texto = nivel.texto)
    }
}

@Composable
private fun MultasCard(plate: String?) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(14.dp), color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            TituloConNivel("Multas", NivelConTexto(NivelEstado.SIN_DATO, "Sin consultar"))
            Spacer(Modifier.height(8.dp))
            Text("Consulta comparendos en el sistema SIMIT", color = RevScopeColors.TextSecondary, fontSize = 12.sp)
            TextButton(
                onClick = { openSimit(context, plate) },
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text("Consultar en SIMIT", color = RevScopeColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MaintenanceCard(nivel: DocumentStatusCalculator.Nivel, detalle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RevScopeColors.Surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            TituloConNivel("Mantenimiento", nivelDocumento(nivel))
            Spacer(Modifier.height(8.dp))
            Text(detalle, color = RevScopeColors.TextPrimary, fontSize = 12.sp)
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
        color = RevScopeColors.Surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
    ) {
        DocCardContent(status, status.nivel == DocumentStatusCalculator.Nivel.SIN_CONFIGURAR) {
            Text("Toca para editar", color = RevScopeColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
private fun SinVehiculoActivo(onOpenProfiles: () -> Unit) {
    EmptyState(
        mensaje = "Sin vehículo activo. Activa un vehículo en Perfiles para ver el estado de sus documentos.",
        accion = AccionEstado("Ir a perfiles", onOpenProfiles),
        icono = Icons.Default.Verified,
        modifier = Modifier.padding(top = 24.dp),
    )
}
