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
import androidx.compose.ui.res.stringResource
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
import com.revscope.core.common.i18n.AndroidStringResolver
import com.revscope.core.common.i18n.Region
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.i18n.detect
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
    region: Region = rememberRegion(),
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
        mantenimientoDetalle = vm.maintenanceDetail(maintenanceEstados),
        zoneBrief = zoneBrief,
        region = region,
        acciones = AccionesAlDia(
            onVolver = onNavigateBack,
            onChequeo = onOpenHealthCheck,
            onPerfiles = onOpenProfiles,
            onMantenimiento = onOpenMaintenance,
            onCambiarLicencia = vm::setLicenseExpiresAt,
        ),
    )
}

@Composable
private fun rememberRegion(): Region {
    val context = LocalContext.current
    return remember { Region.detect(context) }
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
    region: Region,
    acciones: AccionesAlDia,
) {
    val escalaLetra = LocalDensity.current.fontScale
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = stringResource(R.string.workshop_al_dia_title), subtitulo = vehiculo?.nombre, onVolver = acciones.onVolver)
        RejillaAlDia(vehiculo, estados, licenciaVenceEn, mantenimientoNivel, mantenimientoDetalle, zoneBrief, region, acciones, escalaLetra)
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
    region: Region,
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
        estados.find(DocumentStatusCalculator.DocType.SOAT)?.let { item { DocCard(it, region, acciones.onPerfiles) } }
        estados.find(DocumentStatusCalculator.DocType.RTM)?.let {
            item {
                DocCard(it, region, acciones.onPerfiles) {
                    TextButton(onClick = acciones.onChequeo) {
                        Text(stringResource(R.string.workshop_al_dia_mechanical_check), color = RevScopeColors.Accent, style = RevScopeType.label)
                    }
                }
            }
        }
        estados.find(DocumentStatusCalculator.DocType.PICO_Y_PLACA)?.let { item { DocCard(it, region, acciones.onPerfiles) } }
        if (region == Region.CO) item { MultasCard(plate = vehiculo.placa) }
        estados.find(DocumentStatusCalculator.DocType.TODO_RIESGO)?.let { item { DocCard(it, region, acciones.onPerfiles) } }
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
        com.revscope.core.obd.service.ZoneBriefHolder.Source.COMMUNITY -> stringResource(R.string.workshop_al_dia_source_community)
        com.revscope.core.obd.service.ZoneBriefHolder.Source.AI -> stringResource(R.string.workshop_al_dia_source_ai)
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
    region: Region,
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
        DocCardContent(tituloDocumento(status, region), status, sinConfigurar, extra)
    }
}

@Composable
private fun DocCardContent(
    titulo: String,
    status: DocumentStatusCalculator.DocStatus,
    sinConfigurar: Boolean,
    extra: (@Composable () -> Unit)?,
) {
    Column(Modifier.padding(14.dp)) {
        TituloConNivel(titulo, nivelDocumento(status.nivel))
        Spacer(Modifier.height(8.dp))
        Text(
            text = status.detalle,
            color = if (sinConfigurar) RevScopeColors.TextSecondary else RevScopeColors.TextPrimary,
            fontSize = 12.sp,
        )
        if (sinConfigurar) {
            Text(
                stringResource(R.string.workshop_al_dia_tap_to_configure),
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
private fun tituloDocumento(status: DocumentStatusCalculator.DocStatus, region: Region): String =
    when (status.tipo) {
        DocumentStatusCalculator.DocType.SOAT -> stringResource(DocumentStatusCalculator.insuranceTitleRes(region))
        DocumentStatusCalculator.DocType.RTM -> stringResource(DocumentStatusCalculator.inspectionTitleRes(region))
        else -> status.titulo
    }

@Composable
private fun TituloConNivel(titulo: String, nivel: NivelConTexto) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, color = RevScopeColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        NivelBadge(nivel.nivel, texto = stringResource(nivel.textoRes))
    }
}

@Composable
private fun MultasCard(plate: String?) {
    val context = LocalContext.current
    val resolver = remember(context) { AndroidStringResolver(context) }
    Surface(shape = RoundedCornerShape(14.dp), color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            TituloConNivel(stringResource(R.string.workshop_al_dia_fines), NivelConTexto(NivelEstado.SIN_DATO, R.string.workshop_health_level_not_checked))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.workshop_al_dia_fines_description, stringResource(R.string.workshop_al_dia_simit)),
                color = RevScopeColors.TextSecondary,
                fontSize = 12.sp,
            )
            TextButton(
                onClick = { openSimit(context, resolver, plate) },
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    stringResource(R.string.workshop_al_dia_check_simit, stringResource(R.string.workshop_al_dia_simit)),
                    color = RevScopeColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
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
            TituloConNivel(stringResource(R.string.workshop_al_dia_maintenance), nivelDocumento(nivel))
            Spacer(Modifier.height(8.dp))
            Text(detalle, color = RevScopeColors.TextPrimary, fontSize = 12.sp)
        }
    }
}

private fun openSimit(context: Context, resolver: StringResolver, plate: String?) {
    copyPlateToClipboard(context, resolver, plate)
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SIMIT_URL)))
    }.onFailure {
        Toast.makeText(context, resolver.get(R.string.workshop_al_dia_no_browser), Toast.LENGTH_SHORT).show()
    }
}

private fun copyPlateToClipboard(context: Context, resolver: StringResolver, plate: String?) {
    if (plate.isNullOrBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(resolver.get(R.string.workshop_al_dia_plate_clipboard_label), plate))
    Toast.makeText(context, resolver.get(R.string.workshop_al_dia_plate_copied), Toast.LENGTH_SHORT).show()
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
        DocCardContent(status.titulo, status, status.nivel == DocumentStatusCalculator.Nivel.SIN_CONFIGURAR) {
            Text(stringResource(R.string.workshop_al_dia_tap_to_edit), color = RevScopeColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                }) { Text(stringResource(R.string.workshop_al_dia_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.workshop_al_dia_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SinVehiculoActivo(onOpenProfiles: () -> Unit) {
    EmptyState(
        mensaje = stringResource(R.string.workshop_al_dia_no_vehicle),
        accion = AccionEstado(stringResource(R.string.workshop_al_dia_open_profiles), onOpenProfiles),
        icono = Icons.Default.Verified,
        modifier = Modifier.padding(top = 24.dp),
    )
}
