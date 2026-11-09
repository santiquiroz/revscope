package com.revscope.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.revscope.core.common.i18n.LocaleNumbers
import java.util.Locale

/**
 * Editor de la referencia con la que un % de posición se pasa a voltios. [leer] valida el texto (coma o
 * punto, dentro del rango); [textoRestablecer] aparece cuando hay un valor editado y lo quita (null).
 */
@Composable
fun DialogoReferenciaVoltaje(
    valorInicial: String,
    leer: (String) -> Double?,
    onGuardar: (Double?) -> Unit,
    onCerrar: () -> Unit,
    medida: String? = null,
    textoRestablecer: String? = null,
) {
    var texto by remember { mutableStateOf(valorInicial) }
    val valor = leer(texto)
    val locale = LocalConfiguration.current.locales[0]
    val referenciaTipica = LocaleNumbers.decimal(5.0, 1, locale)
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text(stringResource(R.string.ds_voltage_reference_title), color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = { CuerpoReferencia(texto, valor != null, medida, referenciaTipica, locale) { texto = it } },
        confirmButton = {
            TextButton(onClick = { onGuardar(valor) }, enabled = valor != null, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.ds_save), color = if (valor != null) RevScopeColors.Accent else RevScopeColors.TextSecondary, style = RevScopeType.label)
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                textoRestablecer?.let {
                    TextButton(onClick = { onGuardar(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(it, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                    }
                }
                TextButton(onClick = onCerrar, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.ds_cancel), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                }
            }
        },
    )
}

@Composable
private fun CuerpoReferencia(
    texto: String,
    valido: Boolean,
    medida: String?,
    referenciaTipica: String,
    locale: Locale,
    onTexto: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ds_voltage_reference_explanation, referenciaTipica),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        medida?.let {
            Text(stringResource(R.string.ds_voltage_reference_session, it), color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        }
        OutlinedTextField(
            value = texto,
            onValueChange = onTexto,
            label = { Text(stringResource(R.string.ds_volts)) },
            isError = !valido,
            supportingText = {
                Text(
                    stringResource(
                        R.string.ds_voltage_reference_range,
                        LocaleNumbers.decimal(3.0, 1, locale),
                        LocaleNumbers.decimal(5.5, 1, locale),
                    ),
                    color = if (valido) RevScopeColors.TextSecondary else RevScopeColors.Danger,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
