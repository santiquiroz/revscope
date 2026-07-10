package com.revscope.feature.workshop

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.MaintenanceItemEntity
import com.revscope.core.obd.legal.DocumentStatusCalculator.Nivel
import com.revscope.core.obd.trip.MaintenanceCalculator
import kotlin.math.roundToLong

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)
private val NivelOkColor = Color(0xFF4CAF50)
private val NivelAtencionColor = Color(0xFFFFC107)
private val NivelVencidoColor = Color(0xFFFF5252)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    onNavigateBack: () -> Unit,
    vm: MaintenanceViewModel = hiltViewModel(),
) {
    val profile by vm.activeProfile.collectAsState()
    val estados by vm.estados.collectAsState()
    val odometroActual by vm.odometroActual.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MaintenanceItemEntity?>(null) }

    Column(Modifier.fillMaxSize().background(BgColor).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextColor)
            }
            Text("Mantenimiento", color = TextColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        if (profile == null) {
            EmptyProfileState()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OdometerField(odometroActual = odometroActual, onSave = vm::actualizarOdometro) }
            items(estados, key = { it.item.id }) { estado ->
                MaintenanceItemCard(
                    estado = estado,
                    odometroActual = odometroActual,
                    onRegistrarServicio = { vm.registrarServicio(estado.item) },
                    onEditarIntervalo = { editingItem = estado.item },
                )
            }
            item { AddItemButton(onClick = { showAddDialog = true }) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showAddDialog) {
        AddItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { nombre, intervalo ->
                vm.agregarItem(nombre, intervalo)
                showAddDialog = false
            },
        )
    }

    editingItem?.let { item ->
        EditIntervalDialog(
            item = item,
            onDismiss = { editingItem = null },
            onConfirm = { nuevoIntervalo ->
                vm.actualizarIntervalo(item, nuevoIntervalo)
                editingItem = null
            },
        )
    }
}

@Composable
private fun EmptyProfileState() {
    Column(
        Modifier.fillMaxSize().padding(top = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Activa un vehículo en Perfiles para configurar su mantenimiento",
            color = TextMutedColor,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun OdometerField(odometroActual: Double, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(odometroActual) {
        if (!edited) text = odometroActual.roundToLong().toString()
    }
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Kilometraje actual", color = TextMutedColor, fontSize = 12.sp)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; edited = true },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = SurfaceColor,
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        text.toDoubleOrNull()?.let {
                            onSave(it)
                            edited = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.Black),
                ) { Text("Guardar") }
            }
        }
    }
}

@Composable
private fun MaintenanceItemCard(
    estado: MaintenanceCalculator.ItemStatus,
    odometroActual: Double,
    onRegistrarServicio: () -> Unit,
    onEditarIntervalo: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    estado.item.nombre,
                    color = TextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEditarIntervalo) {
                    Icon(Icons.Default.Edit, "Editar intervalo", tint = TextMutedColor)
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progresoIntervalo(estado, odometroActual) },
                color = nivelColor(estado.nivel),
                trackColor = BgColor,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    kmRestantesTexto(estado.kmRestantes),
                    color = nivelColor(estado.nivel),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRegistrarServicio, contentPadding = PaddingValues(0.dp)) {
                    Text("Registrar servicio", color = AccentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "Cada ${estado.item.intervaloKm.roundToLong()} km",
                color = TextMutedColor,
                fontSize = 11.sp,
            )
        }
    }
}

private fun progresoIntervalo(estado: MaintenanceCalculator.ItemStatus, odometroActual: Double): Float {
    if (estado.item.intervaloKm <= 0) return 0f
    val consumido = odometroActual - estado.item.ultimoServicioKm
    return (consumido / estado.item.intervaloKm).toFloat().coerceIn(0f, 1f)
}

private fun kmRestantesTexto(kmRestantes: Double): String {
    val km = kmRestantes.roundToLong()
    return if (km <= 0) "Vencido hace ${-km} km" else "Faltan $km km"
}

private fun nivelColor(nivel: Nivel): Color = when (nivel) {
    Nivel.OK -> NivelOkColor
    Nivel.ATENCION -> NivelAtencionColor
    Nivel.VENCIDO -> NivelVencidoColor
    Nivel.SIN_CONFIGURAR -> TextMutedColor
}

@Composable
private fun AddItemButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, tint = AccentColor)
            Spacer(Modifier.width(8.dp))
            Text("Agregar ítem", color = AccentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddItemDialog(onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var intervalo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo ítem de mantenimiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = intervalo,
                    onValueChange = { intervalo = it },
                    label = { Text("Intervalo en km") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                intervalo.toDoubleOrNull()?.let { onConfirm(nombre, it) }
            }) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun EditIntervalDialog(
    item: MaintenanceItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var intervalo by remember { mutableStateOf(item.intervaloKm.roundToLong().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Intervalo de ${item.nombre}") },
        text = {
            OutlinedTextField(
                value = intervalo,
                onValueChange = { intervalo = it },
                label = { Text("Intervalo en km") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                intervalo.toDoubleOrNull()?.let { onConfirm(it) }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
