package com.revscope.feature.workshop.taller.informe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InformeContent(
    estado: InformeUi,
    acciones: AccionesInforme,
    vistaPrevia: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = estado.titulo, onVolver = acciones.onVolver)
        ContenidoInforme(estado, acciones.onReintentar, vistaPrevia, Modifier.weight(1f))
        if (!estado.cargando && estado.error == null) AccionesExportacion(acciones)
    }
    if (estado.editorVisible) EditorInterpretacion(estado, acciones)
}

@Composable
private fun ContenidoInforme(
    estado: InformeUi,
    onReintentar: () -> Unit,
    vistaPrevia: @Composable (Modifier) -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            estado.cargando -> CircularProgressIndicator(color = RevScopeColors.Accent)
            estado.error != null -> ErrorState(estado.error, onReintentar)
            else -> vistaPrevia(Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccionesExportacion(acciones: AccionesInforme) {
    Surface(color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Button(onClick = acciones.onCompartir, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text("Compartir", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = acciones.onImprimir, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Text("Imprimir o PDF", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = acciones.onEditarInterpretacion, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Text("Editar interpretación", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun EditorInterpretacion(estado: InformeUi, acciones: AccionesInforme) {
    AlertDialog(
        onDismissRequest = acciones.onCancelarEdicion,
        title = { Text("Interpretación del técnico", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Esta nota aparece separada de los hallazgos calculados por RevScope.",
                    color = RevScopeColors.TextSecondary,
                    style = RevScopeType.body,
                )
                OutlinedTextField(
                    value = estado.borradorInterpretacion,
                    onValueChange = acciones.onCambiarInterpretacion,
                    label = { Text("Interpretación") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.onGuardarInterpretacion, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.onCancelarEdicion, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancelar")
            }
        },
        containerColor = RevScopeColors.Surface,
    )
}
