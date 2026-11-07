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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.revscope.core.common.i18n.AndroidStringResolver
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.pruebas.CalibracionVoltaje
import com.revscope.core.obd.taller.pruebas.DesfaseVoltaje
import com.revscope.feature.workshop.R
import java.util.Locale

// ── Fila de ajustes: el desfase de AT RV del vehículo ────────────────────────

@Composable
internal fun FilaDesfase(desfase: DesfaseVoltaje, onCalibrar: () -> Unit) {
    val context = LocalContext.current
    val resolver = remember(context) { AndroidStringResolver(context) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.workshop_prueba_desfase_fila_titulo), color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            Text(
                CalibracionVoltaje.texto(desfase, resolver),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.body.copy(fontFeatureSettings = "tnum"),
            )
        }
        TextButton(onClick = onCalibrar, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.workshop_prueba_desfase_calibrar), color = RevScopeColors.Accent, style = RevScopeType.label)
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
        title = {
            Text(stringResource(R.string.workshop_prueba_desfase_dialogo_titulo), color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        },
        text = {
            CuerpoDesfase(texto, estado.voltajeAdaptador, nuevo, { texto = it }, onQuitar = { onGuardar(null) }.takeIf { estado.desfase.calibrado })
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(multimetro) }, enabled = nuevo != null, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    stringResource(R.string.workshop_prueba_desfase_guardar),
                    color = if (nuevo != null) RevScopeColors.Accent else RevScopeColors.TextSecondary,
                    style = RevScopeType.label,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workshop_prueba_desfase_cancelar), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
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
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.workshop_prueba_desfase_explicacion),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        Text(
            adaptadorV?.let { stringResource(R.string.workshop_prueba_desfase_adaptador_marca, voltios(it, locale)) }
                ?: stringResource(R.string.workshop_prueba_desfase_sin_lectura),
            color = if (adaptadorV != null) RevScopeColors.TextPrimary else RevScopeColors.Warning,
            style = RevScopeType.body.copy(fontFeatureSettings = "tnum"),
        )
        OutlinedTextField(
            value = texto,
            onValueChange = onTexto,
            label = { Text(stringResource(R.string.workshop_prueba_desfase_multimetro)) },
            isError = escrito && nuevo == null,
            supportingText = { Ayuda(nuevo, escrito, locale) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        onQuitar?.let {
            TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workshop_prueba_desfase_quitar), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        }
    }
}

@Composable
private fun Ayuda(nuevo: DesfaseVoltaje?, escrito: Boolean, locale: Locale) {
    val texto = when {
        nuevo != null -> stringResource(R.string.workshop_prueba_desfase_ayuda_valor, LocaleNumbers.signed(nuevo.voltios, 2, locale))
        escrito -> stringResource(R.string.workshop_prueba_desfase_ayuda_rango)
        else -> stringResource(R.string.workshop_prueba_desfase_ayuda_ejemplo, LocaleNumbers.decimal(EJEMPLO_V, 1, locale))
    }
    Text(texto, color = if (escrito && nuevo == null) RevScopeColors.Danger else RevScopeColors.TextSecondary)
}

private const val EJEMPLO_V = 12.6

private fun voltios(v: Double, locale: Locale) = "${LocaleNumbers.decimal(v, 1, locale)} V"
