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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.R as ObdR
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.modelo.NotaModelo
import com.revscope.core.obd.taller.modelo.RepuestoModelo
import com.revscope.core.obd.taller.modelo.TipoNota
import com.revscope.core.obd.taller.modelo.TipoRepuesto
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.feature.workshop.R
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
        BarraConVolver(stringResource(R.string.workshop_modelo_title), acciones.onVolver, subtitulo = estado.vehiculo)
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
        stringResource(R.string.workshop_modelo_empty),
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
            Text(stringResource(R.string.workshop_modelo_view_references), color = RevScopeColors.Accent, style = RevScopeType.label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TarjetaEcu(modelo: ConocimientoModelo) {
    EncabezadoSeccion(stringResource(R.string.workshop_modelo_ecu_header))
    TarjetaTaller {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(modelo.ecu ?: stringResource(R.string.workshop_modelo_ecu_unknown), color = RevScopeColors.TextPrimary, style = RevScopeType.title)
                Fuente(modelo.fuenteEcu ?: stringResource(R.string.workshop_modelo_no_source))
            }
        }
        Text(modelo.notasProtocolo, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
    }
}

@Composable
private fun SeccionNotas(protocolo: String, notas: List<NotaModelo>) {
    if (notas.isEmpty() && protocolo.isBlank()) return
    EncabezadoSeccion(stringResource(R.string.workshop_modelo_notes_header))
    notas.forEach { nota ->
        TarjetaTaller {
            Text(stringResource(etiqueta(nota.tipo)), color = color(nota.tipo), style = RevScopeType.label)
            Text(nota.texto, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            Fuente(nota.fuente)
        }
    }
}

@Composable
private fun SeccionRepuestos(repuestos: List<RepuestoModelo>, onCopiar: (String) -> Unit) {
    if (repuestos.isEmpty()) return
    EncabezadoSeccion(stringResource(R.string.workshop_modelo_parts_header))
    repuestos.forEach { repuesto -> TarjetaRepuesto(repuesto, onCopiar) }
}

@Composable
private fun TarjetaRepuesto(repuesto: RepuestoModelo, onCopiar: (String) -> Unit) {
    TarjetaTaller {
        Text(stringResource(tipo(repuesto.tipo)), color = color(repuesto.tipo), style = RevScopeType.label)
        Text(repuesto.descripcion, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        val descripcionCopiar = stringResource(R.string.workshop_modelo_copy_reference_cd, repuesto.referencia)
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
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = descripcionCopiar },
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.workshop_modelo_copy), color = RevScopeColors.Accent, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (repuesto.nota.isNotBlank()) Text(repuesto.nota, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        Fuente(repuesto.fuente)
    }
}

@Composable
private fun SeccionCableado(cableados: List<CableadoSensor>, onEditar: (String, FuncionCable) -> Unit) {
    if (cableados.isEmpty()) return
    EncabezadoSeccion(stringResource(R.string.workshop_modelo_wiring_header))
    cableados.forEach { cableado ->
        TarjetaTaller {
            Text(cableado.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
            cableado.cables.forEach { cable ->
                val etiqueta = etiquetaCable(cable.funcion)
                val descripcionEditar = stringResource(R.string.workshop_modelo_edit_color_cd, etiqueta)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(etiqueta, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                        Text(cable.color ?: stringResource(R.string.workshop_modelo_color_unknown), color = RevScopeColors.TextSecondary, style = RevScopeType.body)
                    }
                    TextButton(
                        onClick = { onEditar(cableado.sensor, cable.funcion) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = descripcionEditar },
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.workshop_modelo_edit), color = RevScopeColors.Accent, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
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
        Text(stringResource(R.string.workshop_modelo_source, texto), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DialogoCable(edicion: EdicionCableUi, acciones: AccionesConocimientoModelo) {
    AlertDialog(
        onDismissRequest = acciones.onCerrarDialogo,
        containerColor = RevScopeColors.SurfaceHigh,
        title = {
            Text(
                stringResource(R.string.workshop_modelo_color_dialog_title, edicion.etiqueta),
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.title,
            )
        },
        text = {
            OutlinedTextField(
                value = edicion.color,
                onValueChange = acciones.onCambiarColor,
                label = { Text(stringResource(R.string.workshop_modelo_wire_color)) },
                supportingText = { Text(stringResource(R.string.workshop_modelo_wire_color_hint), style = RevScopeType.bodySmall) },
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
                Text(stringResource(R.string.workshop_modelo_save), color = RevScopeColors.Accent, style = RevScopeType.label)
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.onCerrarDialogo, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workshop_modelo_cancel), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        },
    )
}

@Composable
private fun etiquetaCable(funcion: FuncionCable): String = stringResource(
    when (funcion) {
        FuncionCable.REF_5V -> ObdR.string.obd_multimeter_wire_reference
        FuncionCable.MASA -> ObdR.string.obd_multimeter_wire_earth
        FuncionCable.SENAL -> ObdR.string.obd_multimeter_wire_signal
        FuncionCable.ALIMENTACION_12V -> ObdR.string.obd_multimeter_wire_power
        FuncionCable.CALEFACTOR -> ObdR.string.obd_multimeter_wire_heater
        FuncionCable.OTRO -> ObdR.string.obd_multimeter_wire_other
    },
)

private fun etiqueta(tipo: TipoNota): Int = when (tipo) {
    TipoNota.NOTA -> R.string.workshop_modelo_note_note
    TipoNota.EJEMPLO -> R.string.workshop_modelo_note_example
    TipoNota.PENDIENTE -> R.string.workshop_modelo_note_pending
    TipoNota.FUENTE -> R.string.workshop_modelo_note_source
}

private fun color(tipo: TipoNota) = when (tipo) {
    TipoNota.PENDIENTE -> RevScopeColors.Warning
    TipoNota.EJEMPLO -> RevScopeColors.Accent
    TipoNota.NOTA, TipoNota.FUENTE -> RevScopeColors.TextSecondary
}

private fun tipo(tipo: TipoRepuesto): Int = when (tipo) {
    TipoRepuesto.OEM -> R.string.workshop_modelo_part_oem
    TipoRepuesto.NO_EQUIVALENTE -> R.string.workshop_modelo_part_not_equivalent
    TipoRepuesto.ALTERNATIVO -> R.string.workshop_modelo_part_alternative
}

private fun color(tipo: TipoRepuesto) = when (tipo) {
    TipoRepuesto.OEM -> RevScopeColors.Success
    TipoRepuesto.NO_EQUIVALENTE -> RevScopeColors.Warning
    TipoRepuesto.ALTERNATIVO -> RevScopeColors.TextSecondary
}
