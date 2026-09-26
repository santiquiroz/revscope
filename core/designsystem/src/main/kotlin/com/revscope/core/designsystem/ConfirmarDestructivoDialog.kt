package com.revscope.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Confirmación antes de una acción que no se puede deshacer. [avisos] dice qué se pierde y qué se
 * conserva; [contenidoExtra] admite, por ejemplo, una casilla de declaración. El botón de confirmar
 * es rojo con texto oscuro (5,4:1) y «Cancelar» es la salida por defecto.
 */
@Composable
fun ConfirmarDestructivoDialog(
    titulo: String,
    textoConfirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    avisos: List<String> = emptyList(),
    mensaje: String? = null,
    confirmarHabilitado: Boolean = true,
    textoCancelar: String = "Cancelar",
    contenidoExtra: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = RevScopeColors.SurfaceHigh,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = RevScopeColors.Danger) },
        title = { Text(titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            CuerpoConfirmacion(mensaje, avisos, contenidoExtra)
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                enabled = confirmarHabilitado,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RevScopeColors.Danger,
                    contentColor = RevScopeColors.Background,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(textoConfirmar, style = RevScopeType.label) }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(textoCancelar, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        },
    )
}

@Composable
private fun CuerpoConfirmacion(mensaje: String?, avisos: List<String>, contenidoExtra: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        mensaje?.let { Text(it, color = RevScopeColors.TextPrimary, style = RevScopeType.body) }
        avisos.forEach { AvisoConfirmacion(it) }
        contenidoExtra()
    }
}

@Composable
private fun AvisoConfirmacion(texto: String) {
    val lado = with(LocalDensity.current) { 16.sp.toDp() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = RevScopeColors.Warning,
            modifier = Modifier.padding(top = 2.dp).size(lado),
        )
        Text(texto, color = RevScopeColors.TextSecondary, style = RevScopeType.body, modifier = Modifier.weight(1f))
    }
}
