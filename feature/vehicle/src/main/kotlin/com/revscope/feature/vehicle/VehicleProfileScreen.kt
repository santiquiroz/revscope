package com.revscope.feature.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val DangerColor = Color(0xFFFF3040)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleProfileScreen(
    connectionVm: ConnectionViewModel = hiltViewModel(),
    vm: VehicleViewModel = hiltViewModel(),
) {
    val profiles by vm.profiles.collectAsState()
    val formName by vm.formName.collectAsState()
    val formType by vm.formType.collectAsState()
    val formVin by vm.formVin.collectAsState()
    val formEnabledPids by vm.formEnabledPids.collectAsState()
    val editingProfile by vm.editingProfile.collectAsState()
    val formMaxRpm by vm.formMaxRpm.collectAsState()
    val formRedlineRpm by vm.formRedlineRpm.collectAsState()
    val vinStatus by vm.vinStatus.collectAsState()
    val formPlate by vm.formPlate.collectAsState()
    val formPicoPlacaCity by vm.formPicoPlacaCity.collectAsState()
    val formSoatExpiresAt by vm.formSoatExpiresAt.collectAsState()
    val formRtmExpiresAt by vm.formRtmExpiresAt.collectAsState()
    val formInsuranceExpiresAt by vm.formInsuranceExpiresAt.collectAsState()
    val activeProfile by connectionVm.activeProfile.collectAsState()
    val connectionState by connectionVm.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        TopAppBar(
            title = { Text("Perfiles de vehículo", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Existing profiles — tap to edit
            if (profiles.isNotEmpty()) {
                Text(
                    "Perfiles guardados (toca uno para editarlo)",
                    color = TextMutedColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                profiles.forEach { profile ->
                    ProfileItem(
                        profile = profile,
                        isEditing = editingProfile?.id == profile.id,
                        isActive = activeProfile?.id == profile.id,
                        onClick = { vm.startEditing(profile) },
                        onActivate = { connectionVm.setActiveProfile(profile) },
                        onDelete = { vm.deleteProfile(profile.id) },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // Profile form (create or edit)
            Text(
                editingProfile?.let { "Editando: ${it.name}" } ?: "Nuevo perfil",
                color = if (editingProfile != null) AccentColor else TextMutedColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )

            OutlinedTextField(
                value = formName,
                onValueChange = { vm.setName(it) },
                label = { Text("Nombre del vehículo", color = TextMutedColor) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = SurfaceHighColor,
                    focusedTextColor = TextPrimaryColor,
                    unfocusedTextColor = TextPrimaryColor,
                ),
            )

            // Type picker
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TypeChip(
                    label = "Auto",
                    icon = { Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(16.dp)) },
                    selected = formType == "CAR",
                    onClick = { vm.setType("CAR") },
                )
                TypeChip(
                    label = "Moto",
                    icon = { Icon(Icons.Default.TwoWheeler, null, modifier = Modifier.size(16.dp)) },
                    selected = formType == "MOTORCYCLE",
                    onClick = { vm.setType("MOTORCYCLE") },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = formVin,
                    onValueChange = { vm.setVin(it) },
                    label = { Text("VIN — activa el perfil solo al conectar", color = TextMutedColor, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = SurfaceHighColor,
                        focusedTextColor = TextPrimaryColor,
                        unfocusedTextColor = TextPrimaryColor,
                    ),
                )
                Button(
                    onClick = { vm.readVinFromVehicle(connectionVm) },
                    enabled = connectionState is ConnectionState.Connected,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
                ) {
                    Text("Leer VIN", color = TextPrimaryColor, fontSize = 12.sp)
                }
            }
            vinStatus?.let { Text(it, color = TextMutedColor, fontSize = 11.sp) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = formMaxRpm,
                    onValueChange = { vm.setMaxRpm(it) },
                    label = { Text("RPM máx gauge", color = TextMutedColor, fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = SurfaceHighColor,
                        focusedTextColor = TextPrimaryColor,
                        unfocusedTextColor = TextPrimaryColor,
                    ),
                )
                OutlinedTextField(
                    value = formRedlineRpm,
                    onValueChange = { vm.setRedlineRpm(it) },
                    label = { Text("Zona roja RPM", color = TextMutedColor, fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = SurfaceHighColor,
                        focusedTextColor = TextPrimaryColor,
                        unfocusedTextColor = TextPrimaryColor,
                    ),
                )
            }

            // PID enablement
            Text("PIDs activos", color = TextMutedColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.availablePids.forEach { def ->
                    val enabled = def.pid in formEnabledPids
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (enabled) AccentColor else SurfaceHighColor,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { vm.togglePid(def.pid) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = def.nameEs,
                            fontSize = 11.sp,
                            color = if (enabled) BgColor else TextMutedColor,
                            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            // Documents and pico y placa
            Text("Documentos y normativa", color = TextMutedColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)

            OutlinedTextField(
                value = formPlate,
                onValueChange = { vm.setPlate(it) },
                label = { Text("Placa", color = TextMutedColor, fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = SurfaceHighColor,
                    focusedTextColor = TextPrimaryColor,
                    unfocusedTextColor = TextPrimaryColor,
                ),
            )

            Text("Pico y placa", color = TextMutedColor, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextChip(
                    label = "Medellín",
                    selected = formPicoPlacaCity == "medellin",
                    onClick = { vm.setPicoPlacaCity("medellin") },
                )
                TextChip(
                    label = "Ninguna",
                    selected = formPicoPlacaCity == null,
                    onClick = { vm.setPicoPlacaCity(null) },
                )
            }

            DateField(
                label = "SOAT vence",
                valueMs = formSoatExpiresAt,
                onValueChange = { vm.setSoatExpiresAt(it) },
            )
            DateField(
                label = "Tecnomecánica vence",
                valueMs = formRtmExpiresAt,
                onValueChange = { vm.setRtmExpiresAt(it) },
            )
            DateField(
                label = "Todo riesgo vence",
                valueMs = formInsuranceExpiresAt,
                onValueChange = { vm.setInsuranceExpiresAt(it) },
            )

            Button(
                onClick = { vm.saveProfile() },
                enabled = formName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) {
                Text(
                    if (editingProfile != null) "Actualizar perfil" else "Guardar perfil",
                    color = BgColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (editingProfile != null) {
                Button(
                    onClick = { vm.cancelEditing() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
                ) {
                    Text("Cancelar edición", color = TextPrimaryColor)
                }
            }
        }
    }
}

@Composable
private fun TextChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) AccentColor else SurfaceHighColor,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) BgColor else TextMutedColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

/**
 * Fecha de vencimiento editable con Material3 DatePickerDialog. El picker de Material3 trabaja
 * en UTC (medianoche UTC del día elegido) — se formatea con TimeZone UTC para que la fecha
 * mostrada coincida siempre con la fecha seleccionada, sin importar la zona horaria del equipo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    valueMs: Long?,
    onValueChange: (Long?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val formatter = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceHighColor, RoundedCornerShape(8.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextMutedColor, fontSize = 11.sp)
            Text(
                text = valueMs?.let { formatter.format(Date(it)) } ?: "Sin definir",
                color = if (valueMs != null) TextPrimaryColor else TextMutedColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (valueMs != null) {
            IconButton(onClick = { onValueChange(null) }) {
                Icon(Icons.Default.Clear, contentDescription = "Limpiar $label", tint = TextMutedColor)
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = valueMs)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(pickerState.selectedDateMillis)
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
private fun TypeChip(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = if (selected) AccentColor else SurfaceHighColor,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(16.dp)) {
            icon()
        }
        Text(
            text = label,
            color = if (selected) BgColor else TextMutedColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ProfileItem(
    profile: VehicleProfileEntity,
    isEditing: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isEditing) SurfaceHighColor else SurfaceColor,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (profile.type == "MOTORCYCLE") Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = AccentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.name, color = TextPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "gauge ${profile.maxRpm} · roja ${profile.redlineRpm}" +
                    (profile.vin?.let { " · VIN ${it.takeLast(6)}" } ?: ""),
                color = TextMutedColor,
                fontSize = 11.sp,
            )
        }
        if (isActive) {
            Text(
                "ACTIVO",
                color = AccentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(SurfaceHighColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        } else {
            Button(
                onClick = onActivate,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
            ) {
                Text("Usar", color = TextPrimaryColor, fontSize = 11.sp)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = DangerColor)
        }
    }
}
