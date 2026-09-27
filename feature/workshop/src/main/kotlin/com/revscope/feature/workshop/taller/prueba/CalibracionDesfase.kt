package com.revscope.feature.workshop.taller.prueba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.CalibracionVoltaje
import com.revscope.core.obd.taller.pruebas.DesfaseVoltaje

// ── Fila de ajustes: el desfase de AT RV del vehículo ────────────────────────

@Composable
internal fun FilaDesfase(desfase: DesfaseVoltaje, onCalibrar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Desfase del voltaje del adaptador", color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            Text(
                CalibracionVoltaje.texto(desfase),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.body.copy(fontFeatureSettings = "tnum"),
            )
        }
        TextButton(onClick = onCalibrar, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Calibrar", color = RevScopeColors.Accent, style = RevScopeType.label)
        }
    }
}

// ── Diálogo: lo del multímetro en los bornes contra lo que marca el adaptador ─

@Composable
internal fun DialogoDesfase(estado: PruebaGuiadaUi, onGuardar: (Double?) -> Unit, onCerrar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    val multimetro = CalibracionVoltaje.leer(texto)
    val nuevo = CalibracionVoltaje.desde(multimetro, estado.voltajeAdaptador)
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text("Calibrar el voltaje", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            CuerpoDesfase(texto, estado.voltajeAdaptador, nuevo, { texto = it }, onQuitar = { onGuardar(null) }.takeIf { estado.desfase.calibrado })
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(multimetro) }, enabled = nuevo != null, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Guardar", color = if (nuevo != null) RevScopeColors.Accent else RevScopeColors.TextSecondary, style = RevScopeType.label)
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancelar", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        },
    )
}

// El cuerpo desplaza si no cabe (letra al 200 % en un teléfono pequeño); «Quitar la calibración» va aquí y no
// entre los botones del diálogo, que con dos textos largos no caben en una fila.
@Composable
internal fun CuerpoDesfase(
    texto: String,
    adaptadorV: Double?,
    nuevo: DesfaseVoltaje?,
    onTexto: (String) -> Unit,
    onQuitar: (() -> Unit)? = null,
) {
    val escrito = texto.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            "AT RV mide en el conector OBD con ±0,1-0,2 V (típico). Con el contacto puesto, mide la batería en los " +
                "bornes con el multímetro y anótalo: la diferencia se guarda para este vehículo.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        Text(
            adaptadorV?.let { "El adaptador marca ahora ${voltios(it)}" } ?: "Sin lectura del adaptador: conéctalo y pon el contacto",
            color = if (adaptadorV != null) RevScopeColors.TextPrimary else RevScopeColors.Warning,
            style = RevScopeType.body.copy(fontFeatureSettings = "tnum"),
        )
        OutlinedTextField(
            value = texto,
            onValueChange = onTexto,
            label = { Text("Multímetro (V)") },
            isError = escrito && nuevo == null,
            supportingText = { Ayuda(nuevo, escrito) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        onQuitar?.let {
            TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Quitar la calibración", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        }
    }
}

@Composable
private fun Ayuda(nuevo: DesfaseVoltaje?, escrito: Boolean) {
    val texto = when {
        nuevo != null -> "Desfase: ${FormatoTaller.conSigno(nuevo.voltios, 2)} V"
        escrito -> "Entre 6 y 18 V y a menos de 1 V del adaptador"
        else -> "Por ejemplo 12,6"
    }
    Text(texto, color = if (escrito && nuevo == null) RevScopeColors.Danger else RevScopeColors.TextSecondary)
}

private fun voltios(v: Double) = "${FormatoTaller.numero(v, 1)} V"
