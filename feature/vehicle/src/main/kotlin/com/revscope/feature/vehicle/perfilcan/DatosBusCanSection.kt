package com.revscope.feature.vehicle.perfilcan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.obd.perfilcan.FuenteSenal
import com.revscope.core.obd.perfilcan.ModoFuente

class AccionesBusCan(
    val onElegirPerfil: (String?) -> Unit,
    val onElegirModo: (ModoFuente) -> Unit,
    val onElegirFuente: (String, FuenteSenal) -> Unit,
    val onProbar: (String) -> Unit,
)

@Composable
fun DatosBusCanSection(
    vehiculoId: Long,
    onProbar: (String) -> Unit,
    vm: PerfilCanViewModel = hiltViewModel(),
) {
    LaunchedEffect(vehiculoId) { vm.cargar(vehiculoId) }
    val datos by vm.datos.collectAsState()
    datos?.let {
        DatosBusCanContenido(
            datos = it,
            acciones = AccionesBusCan(vm::elegirPerfil, vm::elegirModo, vm::elegirFuente, onProbar),
        )
    }
}

@Composable
fun DatosBusCanContenido(datos: DatosBusCanUi, acciones: AccionesBusCan) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Datos del bus CAN", color = RevScopeColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Señales que tu vehículo publica solo, como la marcha real o la pata lateral.",
            color = RevScopeColors.TextSecondary,
            fontSize = 12.sp,
        )
        SelectorPerfil(datos.perfiles, datos.perfilId, acciones.onElegirPerfil)
        if (datos.perfilId != null) {
            SelectorModo(datos.modo, acciones.onElegirModo)
            ListaSenales(datos, acciones.onProbar)
            FuentesAvanzadas(datos.senales, acciones.onElegirFuente)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorPerfil(perfiles: List<OpcionPerfil>, seleccionado: String?, onElegir: (String?) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val nombre = perfiles.firstOrNull { it.id == seleccionado }?.nombre ?: "Ninguno"
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = nombre,
            onValueChange = {},
            readOnly = true,
            label = { Text("Perfil CAN", color = RevScopeColors.TextSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RevScopeColors.Accent,
                unfocusedBorderColor = RevScopeColors.Surface,
                focusedTextColor = RevScopeColors.TextPrimary,
                unfocusedTextColor = RevScopeColors.TextPrimary,
            ),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text("Ninguno") },
                onClick = {
                    onElegir(null)
                    abierto = false
                },
            )
            perfiles.forEach { perfil ->
                DropdownMenuItem(
                    text = { Text(perfil.nombre) },
                    onClick = {
                        onElegir(perfil.id)
                        abierto = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectorModo(modo: ModoFuente, onElegir: (ModoFuente) -> Unit) {
    Column {
        Text("Cómo se leen los datos", color = RevScopeColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        ModoFuente.entries.forEach { opcion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onElegir(opcion) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = opcion == modo,
                    onClick = { onElegir(opcion) },
                    colors = RadioButtonDefaults.colors(selectedColor = RevScopeColors.Accent),
                )
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(TextosBusCan.modo(opcion), color = RevScopeColors.TextPrimary, fontSize = 14.sp)
                    Text(TextosBusCan.descripcionModo(opcion), color = RevScopeColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ListaSenales(datos: DatosBusCanUi, onProbar: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Señales", color = RevScopeColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            "Solo las señales verificadas se usan. Pruébalas en vivo para verificarlas.",
            color = RevScopeColors.TextSecondary,
            fontSize = 12.sp,
        )
        datos.motivoSinPrueba?.let { Text(it, color = RevScopeColors.Warning, fontSize = 12.sp) }
        datos.senales.forEach { senal -> FilaSenal(senal, onProbar) }
    }
}

@Composable
private fun FilaSenal(senal: SenalBusUi, onProbar: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(senal.nombre, color = RevScopeColors.TextPrimary, fontSize = 14.sp)
            Text(
                if (senal.verificada) "Verificada ✓" else "Sin verificar",
                color = if (senal.verificada) RevScopeColors.Success else RevScopeColors.TextMuted,
                fontSize = 12.sp,
            )
        }
        OutlinedButton(onClick = { onProbar(senal.clave) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Probar en vivo", color = RevScopeColors.TextPrimary, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuentesAvanzadas(senales: List<SenalBusUi>, onElegir: (String, FuenteSenal) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    TextButton(onClick = { abierto = !abierto }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(
            if (abierto) "Ocultar fuente por señal" else "Avanzado: fuente por señal",
            color = RevScopeColors.Accent,
            fontSize = 13.sp,
        )
    }
    if (!abierto) return
    senales.forEach { senal ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(senal.nombre, color = RevScopeColors.TextPrimary, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                senal.opciones.forEach { opcion ->
                    ChipSeleccion(
                        texto = TextosBusCan.fuente(opcion),
                        seleccionado = opcion == senal.fuente,
                        onClick = { onElegir(senal.clave, opcion) },
                    )
                }
            }
        }
    }
}
