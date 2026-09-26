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
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text("Referencia de voltaje", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = { CuerpoReferencia(texto, valor != null, medida) { texto = it } },
        confirmButton = {
            TextButton(onClick = { onGuardar(valor) }, enabled = valor != null, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Guardar", color = if (valor != null) RevScopeColors.Accent else RevScopeColors.TextSecondary, style = RevScopeType.label)
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
                    Text("Cancelar", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                }
            }
        },
    )
}

@Composable
private fun CuerpoReferencia(texto: String, valido: Boolean, medida: String?, onTexto: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "La ECU da la posición en % de su referencia (5,0 V típico). Si la mediste con el multímetro, anótala " +
                "aquí: se guarda para este vehículo.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        medida?.let { Text("En esta sesión: $it medida con el multímetro", color = RevScopeColors.TextPrimary, style = RevScopeType.body) }
        OutlinedTextField(
            value = texto,
            onValueChange = onTexto,
            label = { Text("Voltios") },
            isError = !valido,
            supportingText = { Text("Entre 3,0 y 5,5 V", color = if (valido) RevScopeColors.TextSecondary else RevScopeColors.Danger) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
