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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.designsystem.ConfirmarDestructivoDialog
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.legal.CityRegistry
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import com.revscope.core.obd.taller.modelo.ResumenModelo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleProfileScreen(
    onNavigateBack: () -> Unit,
    connectionVm: ConnectionViewModel = hiltViewModel(),
    vm: VehicleViewModel = hiltViewModel(),
) {
    val profiles by vm.profiles.collectAsState()
    val formName by vm.formName.collectAsState()
    val formType by vm.formType.collectAsState()
    val formFuelType by vm.formFuelType.collectAsState()
    val formVin by vm.formVin.collectAsState()
    val formEnabledPids by vm.formEnabledPids.collectAsState()
    val editingProfile by vm.editingProfile.collectAsState()
    val formMaxRpm by vm.formMaxRpm.collectAsState()
    val formRedlineRpm by vm.formRedlineRpm.collectAsState()
    val formGearCount by vm.formGearCount.collectAsState()
    val vinStatus by vm.vinStatus.collectAsState()
    val formPlate by vm.formPlate.collectAsState()
    val formPicoPlacaCity by vm.formPicoPlacaCity.collectAsState()
    val formSoatExpiresAt by vm.formSoatExpiresAt.collectAsState()
    val formRtmExpiresAt by vm.formRtmExpiresAt.collectAsState()
    val formInsuranceExpiresAt by vm.formInsuranceExpiresAt.collectAsState()
    val formKnowledgeKey by vm.formKnowledgeKey.collectAsState()
    val modelosReferencia by vm.modelosReferencia.collectAsState()
    val activeProfile by connectionVm.activeProfile.collectAsState()
    val connectionState by connectionVm.connectionState.collectAsState()
    val lastAdapterAddress by connectionVm.lastAdapterAddress.collectAsState()
    val adapterLinkStatus by vm.adapterLinkStatus.collectAsState()
    val connectedAdapterAddress = lastAdapterAddress.takeIf { connectionState is ConnectionState.Connected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RevScopeColors.Background),
    ) {
        BarraConVolver(titulo = "Perfiles de vehículo", onVolver = onNavigateBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PerfilesGuardados(
                perfiles = profiles,
                estado = EstadoPerfiles(
                    editandoId = editingProfile?.id,
                    activoId = activeProfile?.id,
                    adaptadorConectado = connectedAdapterAddress,
                ),
                onEditar = vm::startEditing,
                onActivar = connectionVm::setActiveProfile,
                onBorrar = { vm.deleteProfile(it.id) },
            )

            // Profile form (create or edit)
            Text(
                editingProfile?.let { "Editando: ${it.name}" } ?: "Nuevo perfil",
                color = if (editingProfile != null) RevScopeColors.Accent else RevScopeColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )

            OutlinedTextField(
                value = formName,
                onValueChange = { vm.setName(it) },
                label = { Text("Nombre del vehículo", color = RevScopeColors.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RevScopeColors.Accent,
                    unfocusedBorderColor = RevScopeColors.SurfaceHigh,
                    focusedTextColor = RevScopeColors.TextPrimary,
                    unfocusedTextColor = RevScopeColors.TextPrimary,
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

            // Fuel type picker
            Text("Combustible", color = RevScopeColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FuelTypeChip(
                    label = "Corriente",
                    selected = formFuelType == "CORRIENTE",
                    onClick = { vm.setFuelType("CORRIENTE") },
                )
                FuelTypeChip(
                    label = "Extra",
                    selected = formFuelType == "EXTRA",
                    onClick = { vm.setFuelType("EXTRA") },
                )
                FuelTypeChip(
                    label = "Diésel",
                    selected = formFuelType == "DIESEL",
                    onClick = { vm.setFuelType("DIESEL") },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = formVin,
                    onValueChange = { vm.setVin(it) },
                    label = { Text("VIN — activa el perfil solo al conectar", color = RevScopeColors.TextSecondary, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RevScopeColors.Accent,
                        unfocusedBorderColor = RevScopeColors.SurfaceHigh,
                        focusedTextColor = RevScopeColors.TextPrimary,
                        unfocusedTextColor = RevScopeColors.TextPrimary,
                    ),
                )
                Button(
                    onClick = { vm.readVinFromVehicle(connectionVm) },
                    enabled = connectionState is ConnectionState.Connected,
                    colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.SurfaceHigh),
                ) {
                    Text("Leer VIN", color = RevScopeColors.TextPrimary, fontSize = 12.sp)
                }
            }
            vinStatus?.let { Text(it, color = RevScopeColors.TextSecondary, fontSize = 12.sp) }

            editingProfile?.let { profile ->
                AdapterLinkSection(
                    adapterAddress = profile.adapterAddress,
                    connectedAdapterAddress = connectedAdapterAddress,
                    isConnected = connectionState is ConnectionState.Connected,
                    linkStatus = adapterLinkStatus,
                    onLink = { vm.linkConnectedAdapter() },
                    onUnlink = { vm.unlinkAdapter() },
                )
            }

            ModeloReferenciaDropdown(
                modelos = modelosReferencia.filter { it.tipo.name == formType },
                seleccionado = formKnowledgeKey,
                onSeleccionar = vm::setKnowledgeKey,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = formMaxRpm,
                    onValueChange = { vm.setMaxRpm(it) },
                    label = { Text("RPM máx gauge", color = RevScopeColors.TextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RevScopeColors.Accent,
                        unfocusedBorderColor = RevScopeColors.SurfaceHigh,
                        focusedTextColor = RevScopeColors.TextPrimary,
                        unfocusedTextColor = RevScopeColors.TextPrimary,
                    ),
                )
                OutlinedTextField(
                    value = formRedlineRpm,
                    onValueChange = { vm.setRedlineRpm(it) },
                    label = { Text("Zona roja RPM", color = RevScopeColors.TextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RevScopeColors.Accent,
                        unfocusedBorderColor = RevScopeColors.SurfaceHigh,
                        focusedTextColor = RevScopeColors.TextPrimary,
                        unfocusedTextColor = RevScopeColors.TextPrimary,
                    ),
                )
                OutlinedTextField(
                    value = formGearCount,
                    onValueChange = { vm.setGearCount(it) },
                    label = { Text("Marchas", color = RevScopeColors.TextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RevScopeColors.Accent,
                        unfocusedBorderColor = RevScopeColors.SurfaceHigh,
                        focusedTextColor = RevScopeColors.TextPrimary,
                        unfocusedTextColor = RevScopeColors.TextPrimary,
                    ),
                )
            }

            // PID enablement
            Text("PIDs activos", color = RevScopeColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.availablePids.forEach { def ->
                    val enabled = def.pid in formEnabledPids
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (enabled) RevScopeColors.Accent else RevScopeColors.SurfaceHigh,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { vm.togglePid(def.pid) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = def.nameEs,
                            fontSize = 12.sp,
                            color = if (enabled) RevScopeColors.Background else RevScopeColors.TextSecondary,
                            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            // Documents and pico y placa
            Text("Documentos y normativa", color = RevScopeColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)

            OutlinedTextField(
                value = formPlate,
                onValueChange = { vm.setPlate(it) },
                label = { Text("Placa", color = RevScopeColors.TextSecondary, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RevScopeColors.Accent,
                    unfocusedBorderColor = RevScopeColors.SurfaceHigh,
                    focusedTextColor = RevScopeColors.TextPrimary,
                    unfocusedTextColor = RevScopeColors.TextPrimary,
                ),
            )

            PicoYPlacaCityDropdown(
                selectedCityId = formPicoPlacaCity,
                onCitySelected = { vm.setPicoPlacaCity(it) },
            )

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
                colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Accent),
            ) {
                Text(
                    if (editingProfile != null) "Actualizar perfil" else "Guardar perfil",
                    color = RevScopeColors.Background,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (editingProfile != null) {
                Button(
                    onClick = { vm.cancelEditing() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.SurfaceHigh),
                ) {
                    Text("Cancelar edición", color = RevScopeColors.TextPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeloReferenciaDropdown(
    modelos: List<ResumenModelo>,
    seleccionado: String?,
    onSeleccionar: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val nombre = modelos.firstOrNull { it.clave == seleccionado }?.nombre ?: "Sin modelo de referencia"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = nombre,
            onValueChange = {},
            readOnly = true,
            label = { Text("Modelo de referencia", color = RevScopeColors.TextSecondary) },
            supportingText = {
                Text(
                    "Aporta ECU, repuestos, cableado y referencias citadas.",
                    color = RevScopeColors.TextSecondary,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RevScopeColors.Accent,
                unfocusedBorderColor = RevScopeColors.SurfaceHigh,
                focusedTextColor = RevScopeColors.TextPrimary,
                unfocusedTextColor = RevScopeColors.TextPrimary,
            ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sin modelo de referencia") },
                onClick = {
                    onSeleccionar(null)
                    expanded = false
                },
            )
            modelos.forEach { modelo ->
                DropdownMenuItem(
                    text = { Text(modelo.nombre) },
                    onClick = {
                        onSeleccionar(modelo.clave)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Dropdown "Pico y placa": Ninguna + ciudades de [CityRegistry], guarda el id seleccionado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PicoYPlacaCityDropdown(
    selectedCityId: String?,
    onCitySelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = CityRegistry.CITIES.firstOrNull { it.id == selectedCityId }?.nombre ?: "Ninguna"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Pico y placa", color = RevScopeColors.TextSecondary, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RevScopeColors.Accent,
                unfocusedBorderColor = RevScopeColors.SurfaceHigh,
                focusedTextColor = RevScopeColors.TextPrimary,
                unfocusedTextColor = RevScopeColors.TextPrimary,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Ninguna") },
                onClick = {
                    onCitySelected(null)
                    expanded = false
                },
            )
            CityRegistry.CITIES.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city.nombre) },
                    onClick = {
                        onCitySelected(city.id)
                        expanded = false
                    },
                )
            }
        }
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
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = RevScopeColors.TextSecondary, fontSize = 12.sp)
            Text(
                text = valueMs?.let { formatter.format(Date(it)) } ?: "Sin definir",
                color = if (valueMs != null) RevScopeColors.TextPrimary else RevScopeColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (valueMs != null) {
            IconButton(onClick = { onValueChange(null) }) {
                Icon(Icons.Default.Clear, contentDescription = "Limpiar $label", tint = RevScopeColors.TextSecondary)
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

/** Muestra el adaptador vinculado al perfil en edición y permite vincular/desvincular. */
@Composable
private fun AdapterLinkSection(
    adapterAddress: String?,
    connectedAdapterAddress: String?,
    isConnected: Boolean,
    linkStatus: String?,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
) {
    val isLinkedToConnected = adapterAddress != null && adapterAddress == connectedAdapterAddress
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Adaptador vinculado", color = RevScopeColors.TextSecondary, fontSize = 12.sp)
        Text(
            text = (adapterAddress ?: "Ninguno") + if (isLinkedToConnected) " (conectado ahora)" else "",
            color = if (isLinkedToConnected) RevScopeColors.Accent else RevScopeColors.TextPrimary,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onLink,
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Accent),
            ) {
                Text("Vincular adaptador conectado", color = RevScopeColors.Background, fontSize = 12.sp)
            }
            if (adapterAddress != null) {
                TextButton(onClick = onUnlink) {
                    Text("Desvincular", color = RevScopeColors.Danger, fontSize = 12.sp)
                }
            }
        }
        linkStatus?.let { Text(it, color = RevScopeColors.TextSecondary, fontSize = 12.sp) }
    }
}

@Composable
private fun TypeChip(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = { Box(modifier = Modifier.size(18.dp)) { icon() } },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = RevScopeColors.SurfaceHigh,
            labelColor = RevScopeColors.TextSecondary,
            iconColor = RevScopeColors.TextSecondary,
            selectedContainerColor = RevScopeColors.Accent,
            selectedLabelColor = RevScopeColors.Background,
            selectedLeadingIconColor = RevScopeColors.Background,
        ),
    )
}

private fun fuelTypeLabel(fuelType: String): String = when (fuelType) {
    "EXTRA" -> "Extra"
    "DIESEL" -> "Diésel"
    else -> "Corriente"
}

@Composable
private fun FuelTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ChipSeleccion(texto = label, seleccionado = selected, onClick = onClick)
}

data class EstadoPerfiles(
    val editandoId: Long?,
    val activoId: Long?,
    val adaptadorConectado: String?,
)

@Composable
internal fun PerfilesGuardados(
    perfiles: List<VehicleProfileEntity>,
    estado: EstadoPerfiles,
    onEditar: (VehicleProfileEntity) -> Unit,
    onActivar: (VehicleProfileEntity) -> Unit,
    onBorrar: (VehicleProfileEntity) -> Unit,
) {
    if (perfiles.isEmpty()) return
    var porBorrar by remember { mutableStateOf<VehicleProfileEntity?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Perfiles guardados (toca uno para editarlo)",
            color = RevScopeColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        perfiles.forEach { profile ->
            ProfileItem(
                profile = profile,
                isEditing = estado.editandoId == profile.id,
                isActive = estado.activoId == profile.id,
                connectedAdapterAddress = estado.adaptadorConectado,
                onClick = { onEditar(profile) },
                onActivate = { onActivar(profile) },
                onDelete = { porBorrar = profile },
            )
        }
    }
    porBorrar?.let { perfil ->
        ConfirmarBorrarVehiculoDialog(
            perfil = perfil,
            onConfirmar = {
                porBorrar = null
                onBorrar(perfil)
            },
            onCancelar = { porBorrar = null },
        )
    }
}

@Composable
internal fun ConfirmarBorrarVehiculoDialog(
    perfil: VehicleProfileEntity,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    ConfirmarDestructivoDialog(
        titulo = "¿Eliminar «${perfil.name}»?",
        avisos = listOf(
            "Se borra el perfil con su configuración: PIDs, marchas, placa y vencimientos de documentos. " +
                "No se puede deshacer.",
            "Los viajes de este vehículo se conservan en el historial (filtro «Todos»).",
        ),
        textoConfirmar = "Eliminar vehículo",
        onConfirmar = onConfirmar,
        onCancelar = onCancelar,
    )
}

@Composable
private fun ProfileItem(
    profile: VehicleProfileEntity,
    isEditing: Boolean,
    isActive: Boolean,
    connectedAdapterAddress: String?,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isEditing) RevScopeColors.SurfaceHigh else RevScopeColors.Surface,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (profile.type == "MOTORCYCLE") Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = RevScopeColors.Accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.name, color = RevScopeColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "gauge ${profile.maxRpm} · roja ${profile.redlineRpm} · ${fuelTypeLabel(profile.fuelType)}" +
                    (profile.vin?.let { " · VIN ${it.takeLast(6)}" } ?: ""),
                color = RevScopeColors.TextSecondary,
                fontSize = 12.sp,
            )
            profile.adapterAddress?.let { address ->
                val isConnectedNow = address == connectedAdapterAddress
                Text(
                    text = "Adaptador: $address" + if (isConnectedNow) " (conectado ahora)" else "",
                    color = if (isConnectedNow) RevScopeColors.Accent else RevScopeColors.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (isActive) {
            Text(
                "ACTIVO",
                color = RevScopeColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        } else {
            Button(
                onClick = onActivate,
                colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.SurfaceHigh),
            ) {
                Text("Usar", color = RevScopeColors.TextPrimary, fontSize = 12.sp)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar ${profile.name}", tint = RevScopeColors.Danger)
        }
    }
}
