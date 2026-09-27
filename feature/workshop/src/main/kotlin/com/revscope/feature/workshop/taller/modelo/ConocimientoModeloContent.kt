package com.revscope.feature.workshop.taller.modelo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.modelo.NotaModelo
import com.revscope.core.obd.taller.modelo.RepuestoModelo
import com.revscope.core.obd.taller.modelo.TipoNota
import com.revscope.core.obd.taller.modelo.TipoRepuesto
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.feature.workshop.taller.EncabezadoSeccion
import com.revscope.feature.workshop.taller.TarjetaTaller

data class AccionesConocimientoModelo(
    val onVolver: () -> Unit = {},
    val onReferencias: () -> Unit = {},
    val onCopiar: (String) -> Unit = {},
    val onEditarCable: (String, FuncionCable) -> Unit = { _, _ -> },
    val onCambiarColor: (String) -> Unit = {},
    val onGuardarColor: () -> Unit = {},
    val onCerrarDialogo: () -> Unit = {},
    val onReintentar: () -> Unit = {},
)

@Composable
fun ConocimientoModeloContent(estado: ConocimientoModeloUi, acciones: AccionesConocimientoModelo = AccionesConocimientoModelo()) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver("Conocimiento del modelo", acciones.onVolver, subtitulo = estado.vehiculo)
        when {
            estado.cargando -> CargandoModelo()
            estado.error != null -> ErrorModelo(estado.error, acciones.onReintentar)
            estado.modelo == null -> SinModelo()
            else -> Modelo(estado.modelo, acciones)
        }
    }
    estado.dialogoCable?.let { DialogoCable(it, acciones) }
}

@Composable
private fun CargandoModelo() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RevScopeColors.Accent)
    }
}

@Composable
private fun ErrorModelo(mensaje: String, onReintentar: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        ErrorState(mensaje, onReintentar)
    }
}

@Composable
private fun SinModelo() {
    EmptyState(
        "Este vehículo no tiene un modelo de referencia. Elígelo en Perfiles para ver ECU, repuestos, cableado y fuentes.",
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun Modelo(modelo: ConocimientoModelo, acciones: AccionesConocimientoModelo) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(modelo.nombre, color = RevScopeColors.TextPrimary, style = RevScopeType.title, modifier = Modifier.semantics { heading() })
        TarjetaEcu(modelo)
        SeccionNotas(modelo.notasProtocolo, modelo.notas)
        SeccionRepuestos(modelo.repuestos, acciones.onCopiar)
        SeccionCableado(modelo.cableado, acciones.onEditarCable)
        OutlinedButton(onClick = acciones.onReferencias, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Ver y editar referencias", color = RevScopeColors.Accent, style = RevScopeType.label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TarjetaEcu(modelo: ConocimientoModelo) {
    EncabezadoSeccion("ECU y protocolo")
    TarjetaTaller {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(modelo.ecu ?: "ECU sin identificar", color = RevScopeColors.TextPrimary, style = RevScopeType.title)
                Fuente(modelo.fuenteEcu ?: "Sin fuente registrada")
            }
        }
        Text(modelo.notasProtocolo, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
    }
}

@Composable
private fun SeccionNotas(protocolo: String, notas: List<NotaModelo>) {
    if (notas.isEmpty() && protocolo.isBlank()) return
    EncabezadoSeccion("Notas verificables")
    notas.forEach { nota ->
        TarjetaTaller {
            Text(etiqueta(nota.tipo), color = color(nota.tipo), style = RevScopeType.label)
            Text(nota.texto, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            Fuente(nota.fuente)
        }
    }
}

@Composable
private fun SeccionRepuestos(repuestos: List<RepuestoModelo>, onCopiar: (String) -> Unit) {
    if (repuestos.isEmpty()) return
    EncabezadoSeccion("Repuestos y referencias")
    repuestos.forEach { repuesto -> TarjetaRepuesto(repuesto, onCopiar) }
}

@Composable
private fun TarjetaRepuesto(repuesto: RepuestoModelo, onCopiar: (String) -> Unit) {
    TarjetaTaller {
        Text(tipo(repuesto.tipo), color = color(repuesto.tipo), style = RevScopeType.label)
        Text(repuesto.descripcion, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                repuesto.referencia,
                color = RevScopeColors.Accent,
                style = RevScopeType.label,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onCopiar(repuesto.referencia) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Copiar referencia ${repuesto.referencia}" },
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
                Text("Copiar", color = RevScopeColors.Accent, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (repuesto.nota.isNotBlank()) Text(repuesto.nota, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        Fuente(repuesto.fuente)
    }
}

@Composable
private fun SeccionCableado(cableados: List<CableadoSensor>, onEditar: (String, FuncionCable) -> Unit) {
    if (cableados.isEmpty()) return
    EncabezadoSeccion("Cableado editable")
    cableados.forEach { cableado ->
        TarjetaTaller {
            Text(cableado.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
            cableado.cables.forEach { cable ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(cable.funcion.etiqueta, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                        Text(cable.color ?: "Color sin registrar", color = RevScopeColors.TextSecondary, style = RevScopeType.body)
                    }
                    TextButton(
                        onClick = { onEditar(cableado.sensor, cable.funcion) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Editar color de ${cable.funcion.etiqueta}"
                        },
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
                        Text("Editar", color = RevScopeColors.Accent, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            Fuente(cableado.fuente)
        }
    }
}

@Composable
private fun Fuente(texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Link, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(18.dp))
        Text("Fuente: $texto", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DialogoCable(edicion: EdicionCableUi, acciones: AccionesConocimientoModelo) {
    AlertDialog(
        onDismissRequest = acciones.onCerrarDialogo,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text("Color de ${edicion.etiqueta}", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            OutlinedTextField(
                value = edicion.color,
                onValueChange = acciones.onCambiarColor,
                label = { Text("Color del cable") },
                supportingText = { Text("Déjalo vacío si el color no está confirmado.", style = RevScopeType.bodySmall) },
                textStyle = RevScopeType.body,
                singleLine = false,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RevScopeColors.TextPrimary,
                    unfocusedTextColor = RevScopeColors.TextPrimary,
                    focusedBorderColor = RevScopeColors.Accent,
                    unfocusedBorderColor = RevScopeColors.TextSecondary,
                    focusedLabelColor = RevScopeColors.Accent,
                    unfocusedLabelColor = RevScopeColors.TextSecondary,
                    cursorColor = RevScopeColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = acciones.onGuardarColor, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Guardar", color = RevScopeColors.Accent, style = RevScopeType.label)
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.onCerrarDialogo, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancelar", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        },
    )
}

private fun etiqueta(tipo: TipoNota): String = when (tipo) {
    TipoNota.NOTA -> "Nota"
    TipoNota.EJEMPLO -> "Ejemplo, no especificación del fabricante"
    TipoNota.PENDIENTE -> "Pendiente por confirmar"
    TipoNota.FUENTE -> "Fuente documental"
}

private fun color(tipo: TipoNota) = when (tipo) {
    TipoNota.PENDIENTE -> RevScopeColors.Warning
    TipoNota.EJEMPLO -> RevScopeColors.Accent
    TipoNota.NOTA, TipoNota.FUENTE -> RevScopeColors.TextSecondary
}

private fun tipo(tipo: TipoRepuesto): String = when (tipo) {
    TipoRepuesto.OEM -> "Original (OEM)"
    TipoRepuesto.NO_EQUIVALENTE -> "No equivalente al original"
    TipoRepuesto.ALTERNATIVO -> "Alternativo"
}

private fun color(tipo: TipoRepuesto) = when (tipo) {
    TipoRepuesto.OEM -> RevScopeColors.Success
    TipoRepuesto.NO_EQUIVALENTE -> RevScopeColors.Warning
    TipoRepuesto.ALTERNATIVO -> RevScopeColors.TextSecondary
}
