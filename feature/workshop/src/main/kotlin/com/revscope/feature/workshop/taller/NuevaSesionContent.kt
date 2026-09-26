package com.revscope.feature.workshop.taller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.obd.taller.sesion.Sintoma

data class AccionesNuevaSesion(
    val onVolver: () -> Unit = {},
    val onSintoma: (Sintoma) -> Unit = {},
    val onSintomasTexto: (String) -> Unit = {},
    val onOdometro: (String) -> Unit = {},
    val onNotas: (String) -> Unit = {},
    val onBase: (Long?) -> Unit = {},
    val onAbrir: () -> Unit = {},
    val onConfirmarCierre: () -> Unit = {},
    val onCancelarCierre: () -> Unit = {},
)

@Composable
fun NuevaSesionContent(estado: NuevaSesionEstado, acciones: AccionesNuevaSesion) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = "Nueva sesión", subtitulo = estado.vehiculo, onVolver = acciones.onVolver)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "sintomas") { SeccionSintomas(estado.sintomas, acciones.onSintoma) }
            item(key = "texto") {
                CampoTexto("Descripción", "Qué notas, en tus palabras.", estado.sintomasTexto, acciones.onSintomasTexto)
            }
            item(key = "odometro") { CampoOdometro(estado, acciones.onOdometro) }
            item(key = "notas") {
                CampoTexto("Notas", "Repuestos cambiados, arreglos previos…", estado.notas, acciones.onNotas)
            }
            item(key = "base") { SeccionBase(estado, acciones.onBase) }
            estado.error?.let { item(key = "error") { TextoError(it) } }
        }
        BarraAbrir(estado, acciones.onAbrir)
    }
    estado.porCerrar?.let { DialogoCerrarAnterior(it, acciones) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionSintomas(elegidos: Set<Sintoma>, onSintoma: (Sintoma) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EncabezadoSeccion("Síntomas")
        Text("Marca todos los que apliquen.", color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Sintoma.entries.forEach { sintoma ->
                ChipSeleccion(sintoma.etiqueta, seleccionado = sintoma in elegidos, onClick = { onSintoma(sintoma) })
            }
        }
    }
}

@Composable
private fun CampoTexto(etiqueta: String, ayuda: String, valor: String, onCambio: (String) -> Unit) {
    // Etiqueta corta: una larga envuelve con la letra al 200 % y pisa el borde del campo.
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta, style = RevScopeType.body) },
        supportingText = { Text(ayuda, style = RevScopeType.bodySmall) },
        minLines = LINEAS_TEXTO,
        textStyle = RevScopeType.body,
        colors = coloresCampo(),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val LINEAS_TEXTO = 3

@Composable
private fun CampoOdometro(estado: NuevaSesionEstado, onCambio: (String) -> Unit) {
    val ayuda = when {
        estado.errorOdometro != null -> estado.errorOdometro
        estado.odometroDeEcu -> "Leído de la ECU; corrígelo si el tablero dice otra cosa."
        else -> "Opcional."
    }
    OutlinedTextField(
        value = estado.odometro,
        onValueChange = onCambio,
        label = { Text("Odómetro (km)", style = RevScopeType.body) },
        supportingText = { Text(ayuda, style = RevScopeType.bodySmall) },
        isError = estado.errorOdometro != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = RevScopeType.body.conCifrasTabulares(),
        colors = coloresCampo(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun coloresCampo() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RevScopeColors.TextPrimary,
    unfocusedTextColor = RevScopeColors.TextPrimary,
    focusedBorderColor = RevScopeColors.Accent,
    unfocusedBorderColor = RevScopeColors.TextMuted,
    focusedLabelColor = RevScopeColors.Accent,
    unfocusedLabelColor = RevScopeColors.TextSecondary,
    focusedSupportingTextColor = RevScopeColors.TextSecondary,
    unfocusedSupportingTextColor = RevScopeColors.TextSecondary,
    cursorColor = RevScopeColors.Accent,
)

@Composable
private fun SeccionBase(estado: NuevaSesionEstado, onBase: (Long?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.selectableGroup()) {
        EncabezadoSeccion("Chequeo base para comparar")
        if (estado.bases.isEmpty()) {
            Text(
                "No hay chequeos de salud anteriores de este vehículo. La sesión se abre sin base; si corres un chequeo " +
                    "dentro de la sesión, la próxima podrá compararse con él.",
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.body,
            )
            return
        }
        estado.bases.forEach { base ->
            OpcionBase(base.fecha, base.resumen, estado.baseElegida == base.id) { onBase(base.id) }
        }
        OpcionBase("Sin chequeo base", "No comparar con un chequeo anterior", estado.baseElegida == null) { onBase(null) }
    }
}

@Composable
private fun OpcionBase(titulo: String, detalle: String, elegida: Boolean, onElegir: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = elegida, onClick = onElegir, role = Role.RadioButton),
    ) {
        RadioButton(
            selected = elegida,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = RevScopeColors.Accent, unselectedColor = RevScopeColors.TextSecondary),
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            Text(detalle, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
    }
}

@Composable
private fun TextoError(mensaje: String) {
    Text(
        mensaje,
        color = RevScopeColors.Danger,
        style = RevScopeType.body,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun BarraAbrir(estado: NuevaSesionEstado, onAbrir: () -> Unit) {
    Surface(color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            val texto = if (estado.abriendo) "Abriendo…" else "Abrir sesión"
            BotonPrincipalTaller(texto, onClick = onAbrir, habilitado = estado.puedeAbrir)
        }
    }
}

@Composable
private fun DialogoCerrarAnterior(anterior: SesionPorCerrar, acciones: AccionesNuevaSesion) {
    AlertDialog(
        onDismissRequest = acciones.onCancelarCierre,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text("Ya hay una sesión abierta", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            Text(
                "«${anterior.titulo}», abierta el ${anterior.desde}. Para abrir esta se cierra la anterior; su línea " +
                    "de tiempo se conserva y puedes verla en Sesiones anteriores.",
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.body,
            )
        },
        confirmButton = {
            TextButton(onClick = acciones.onConfirmarCierre, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cerrar la anterior y abrir esta", color = RevScopeColors.Accent, style = RevScopeType.label)
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.onCancelarCierre, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancelar", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        },
    )
}
