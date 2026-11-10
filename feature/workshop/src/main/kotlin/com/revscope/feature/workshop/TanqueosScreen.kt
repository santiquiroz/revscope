package com.revscope.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.FuelUpEntity
import com.revscope.core.designsystem.AccionEstado
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.obd.trip.IntervaloTanqueo
import com.revscope.core.obd.trip.MotivoIgnorado
import com.revscope.core.obd.trip.FuelCostCalculator
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun TanqueosScreen(
    onNavigateBack: () -> Unit,
    onElegirVehiculo: () -> Unit,
    vm: TanqueosViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsState()
    TanqueosContent(
        estado = estado,
        onAgregar = vm::agregar,
        onBorrar = vm::borrar,
        onAplicarFactor = vm::aplicarFactor,
        onVolver = onNavigateBack,
        onElegirVehiculo = onElegirVehiculo,
    )
}

@Composable
fun TanqueosContent(
    estado: TanqueosEstado,
    onAgregar: suspend (String, Boolean) -> Boolean,
    onBorrar: (Long) -> Unit,
    onAplicarFactor: () -> Unit,
    onVolver: () -> Unit,
    onElegirVehiculo: () -> Unit,
) {
    var mostrarDialogo by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = RevScopeColors.Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(RevScopeColors.Background).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.revscope.core.designsystem.R.string.ds_back), tint = RevScopeColors.TextPrimary)
                }
                Text(stringResource(R.string.workshop_fuel_log_title), color = RevScopeColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (estado.perfil == null) {
                EmptyState(
                    mensaje = stringResource(R.string.workshop_fuel_log_choose_vehicle_message),
                    accion = AccionEstado(stringResource(R.string.workshop_fuel_log_choose_vehicle), onElegirVehiculo),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { CalibrationCard(estado, onAplicarFactor) }
                    item { SectionTitle(stringResource(R.string.workshop_fuel_log_intervals)) }
                    items(estado.intervalos.asReversed()) { (intervalo, motivo) -> IntervalCard(intervalo, motivo, estado.enGalones) }
                    item { SectionTitle(stringResource(R.string.workshop_fuel_log_entries)) }
                    items(estado.tanqueos.asReversed(), key = FuelUpEntity::id) { tanqueo -> FuelUpRow(tanqueo, estado.enGalones, onBorrar) }
                    item { Button(onClick = { mostrarDialogo = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.workshop_fuel_log_add)) } }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
    if (mostrarDialogo) FuelUpDialog(onDismiss = { mostrarDialogo = false }, onAgregar = onAgregar, enGalones = estado.enGalones)
}

@Composable
private fun CalibrationCard(estado: TanqueosEstado, onAplicarFactor: () -> Unit) {
    val resultado = estado.calibracion
    val sugerido = resultado.factorSugerido
    Surface(shape = RoundedCornerShape(14.dp), color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.workshop_fuel_log_calibration), color = RevScopeColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.workshop_fuel_log_current_factor, decimal(resultado.factorActual)), color = RevScopeColors.TextPrimary)
            when {
                sugerido != null -> {
                    Text(stringResource(R.string.workshop_fuel_log_suggestion, percent(resultado.desvio ?: 0.0), resultado.usados.size), color = RevScopeColors.TextSecondary)
                    Button(onClick = onAplicarFactor, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.workshop_fuel_log_apply_factor, decimal(sugerido)))
                    }
                }
                resultado.usados.size >= 3 -> Text(
                    stringResource(R.string.workshop_fuel_log_matches, percent(resultado.desvio ?: 0.0)),
                    color = RevScopeColors.TextSecondary,
                )
                resultado.faltan > 0 -> Text(
                    pluralStringResource(R.plurals.workshop_fuel_log_more_intervals, resultado.faltan, resultado.faltan),
                    color = RevScopeColors.TextSecondary,
                )
            }
            Text(stringResource(R.string.workshop_fuel_log_help), color = RevScopeColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun IntervalCard(intervalo: IntervaloTanqueo, motivo: MotivoIgnorado?, enGalones: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${fecha(intervalo.desde)} → ${fecha(intervalo.hasta)}", color = RevScopeColors.TextPrimary, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.workshop_fuel_log_comparison, volumen(intervalo.litrosReales, enGalones), volumen(intervalo.litrosEstimados, enGalones), percent(intervalo.desvio)), color = RevScopeColors.TextSecondary)
            val estado = if (motivo == null) stringResource(R.string.workshop_fuel_log_used) else motivoTexto(motivo)
            Text(estado, color = RevScopeColors.Accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun motivoTexto(motivo: MotivoIgnorado): String = stringResource(
    when (motivo) {
        MotivoIgnorado.ANTES_DEL_FACTOR -> R.string.workshop_fuel_log_ignored_before_factor
        MotivoIgnorado.SIN_DATOS -> R.string.workshop_fuel_log_ignored_no_data
        MotivoIgnorado.INCOMPLETO -> R.string.workshop_fuel_log_ignored_incomplete
        MotivoIgnorado.DESVIO -> R.string.workshop_fuel_log_ignored_deviation
    },
)

@Composable
private fun FuelUpRow(tanqueo: FuelUpEntity, enGalones: Boolean, onBorrar: (Long) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fecha(tanqueo.timestamp), color = RevScopeColors.TextPrimary)
                Text(
                    stringResource(if (tanqueo.fullTank) R.string.workshop_fuel_log_full else R.string.workshop_fuel_log_partial) +
                        " · " + volumen(tanqueo.liters, enGalones),
                    color = RevScopeColors.TextSecondary,
                )
            }
            IconButton(onClick = { onBorrar(tanqueo.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.workshop_fuel_log_delete), tint = RevScopeColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = RevScopeColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun FuelUpDialog(onDismiss: () -> Unit, onAgregar: suspend (String, Boolean) -> Boolean, enGalones: Boolean) {
    var texto by remember { mutableStateOf("") }
    var lleno by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workshop_fuel_log_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it; error = false },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = {
                        val unidad = stringResource(if (enGalones) R.string.workshop_fuel_log_gallons else R.string.workshop_fuel_log_liters)
                        Text(stringResource(R.string.workshop_fuel_log_volume, unidad))
                    },
                    isError = error,
                    supportingText = if (error) ({ Text(stringResource(R.string.workshop_fuel_log_invalid_volume)) }) else null,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = lleno, onCheckedChange = { lleno = it })
                    Text(stringResource(R.string.workshop_fuel_log_full_tank))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !guardando, onClick = {
                scope.launch {
                    guardando = true
                    if (onAgregar(texto, lleno)) onDismiss() else error = true
                    guardando = false
                }
            }) { Text(stringResource(R.string.workshop_fuel_log_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.revscope.core.designsystem.R.string.ds_cancel)) } },
    )
}

@Composable
private fun fecha(timestamp: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(timestamp, locale) { SimpleDateFormat(DATE_PATTERN, locale).format(Date(timestamp)) }
}

@Composable
private fun decimal(valor: Double): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(valor, locale) {
        NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(valor)
    }
}

@Composable
private fun percent(valor: Double): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(valor, locale) { NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(valor) }
}

@Composable
private fun volumen(litros: Double, enGalones: Boolean): String {
    val value = if (enGalones) litros / FuelCostCalculator.LITERS_PER_GALLON else litros
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(value, locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(value) }
    val unit = stringResource(if (enGalones) R.string.workshop_fuel_log_gallons else R.string.workshop_fuel_log_liters)
    return "$formatted $unit"
}

private const val DATE_PATTERN = "d MMM yyyy, HH:mm" // i18n-ignore: locale-neutral date pattern.
