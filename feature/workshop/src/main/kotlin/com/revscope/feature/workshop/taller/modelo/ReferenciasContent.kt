package com.revscope.feature.workshop.taller.modelo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.feature.workshop.taller.TarjetaTaller

data class AccionesReferencias(
    val onVolver: () -> Unit = {},
    val onEditar: (String) -> Unit = {},
    val onRestablecer: (String) -> Unit = {},
    val onCambiarMinimo: (String) -> Unit = {},
    val onCambiarMaximo: (String) -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onCerrarDialogo: () -> Unit = {},
    val onReintentar: () -> Unit = {},
)

@Composable
fun ReferenciasContent(estado: ReferenciasUi, acciones: AccionesReferencias = AccionesReferencias()) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver("Referencias", acciones.onVolver, subtitulo = estado.modelo ?: estado.vehiculo)
        when {
            estado.cargando -> CargandoReferencias()
            estado.error != null -> ErrorReferencias(estado.error, acciones.onReintentar)
            estado.bandas.isEmpty() -> EmptyState("No hay referencias para este tipo de vehículo.", Modifier.padding(16.dp))
            else -> ListaReferencias(estado, acciones)
        }
    }
    estado.dialogo?.let { DialogoBanda(it, acciones) }
}

@Composable
private fun CargandoReferencias() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RevScopeColors.Accent)
    }
}

@Composable
private fun ErrorReferencias(mensaje: String, onReintentar: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        ErrorState(mensaje, onReintentar)
    }
}

@Composable
private fun ListaReferencias(estado: ReferenciasUi, acciones: AccionesReferencias) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Bandas de diagnóstico", color = RevScopeColors.TextPrimary, style = RevScopeType.title, modifier = Modifier.semantics { heading() })
        Text(
            "Los valores Típico son referencias generales de la industria, no especificaciones del fabricante. " +
                "Una banda con Fuente indica de dónde salió; tus cambios siempre aparecen como Editado por ti.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        if (estado.modelo == null) {
            Text(
                "Elige un modelo de referencia en Perfiles para guardar cambios propios.",
                color = RevScopeColors.Warning,
                style = RevScopeType.body,
            )
        }
        estado.bandas.forEach { banda -> TarjetaBanda(banda, acciones) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaBanda(banda: BandaReferencia, acciones: AccionesReferencias) {
    TarjetaTaller {
        Text(nombreBanda(banda.clave), color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        Text(valorBanda(banda), color = RevScopeColors.Accent, style = RevScopeType.numeros)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.BookmarkAdded, contentDescription = null, tint = colorOrigen(banda.origen), modifier = Modifier.size(18.dp))
            Text(origen(banda), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { acciones.onEditar(banda.clave) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Editar ${nombreBanda(banda.clave)}" },
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
                Text("Editar", color = RevScopeColors.Accent, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
            }
            if (banda.origen != OrigenBanda.TIPICO) {
                TextButton(
                    onClick = { acciones.onRestablecer(banda.clave) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Restablecer ${nombreBanda(banda.clave)} a Típico"
                    },
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Text("Restablecer a Típico", color = RevScopeColors.TextSecondary, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun DialogoBanda(dialogo: EdicionBandaUi, acciones: AccionesReferencias) {
    AlertDialog(
        onDismissRequest = acciones.onCerrarDialogo,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text("Editar ${nombreBanda(dialogo.banda.clave)}", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoLimite("Mínimo", dialogo.minimo, acciones.onCambiarMinimo)
                CampoLimite("Máximo", dialogo.maximo, acciones.onCambiarMaximo)
                Text("Unidad: ${dialogo.banda.unidad}", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
                dialogo.error?.let {
                    Text(it, color = RevScopeColors.Danger, style = RevScopeType.body, modifier = Modifier.semantics { contentDescription = "Error: $it" })
                }
                Text("Al guardar, el origen cambiará a «Editado por ti».", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.onGuardar, modifier = Modifier.heightIn(min = 48.dp)) {
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

@Composable
private fun CampoLimite(etiqueta: String, valor: String, onValor: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(etiqueta) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = RevScopeType.numeros,
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
}

internal fun nombreBanda(clave: String): String = NOMBRES_BANDAS[clave] ?: clave.lowercase().replace('_', ' ')

internal fun valorBanda(banda: BandaReferencia): String {
    val minimo = banda.min
    val maximo = banda.max
    return when {
        minimo != null && maximo != null -> "${numero(minimo)}–${numero(maximo)} ${banda.unidad}"
        minimo != null -> "≥ ${numero(minimo)} ${banda.unidad}"
        else -> "≤ ${numero(maximo!!)} ${banda.unidad}"
    }
}

private fun numero(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toInt().toString() else valor.toString().replace('.', ',')

private fun origen(banda: BandaReferencia): String = when (banda.origen) {
    OrigenBanda.TIPICO -> "Típico"
    OrigenBanda.FUENTE -> "Fuente: ${banda.fuente}"
    OrigenBanda.USUARIO -> "Editado por ti"
}

private fun colorOrigen(origen: OrigenBanda) = when (origen) {
    OrigenBanda.TIPICO -> RevScopeColors.TextSecondary
    OrigenBanda.FUENTE -> RevScopeColors.Success
    OrigenBanda.USUARIO -> RevScopeColors.Accent
}

private val NOMBRES_BANDAS = mapOf(
    ClavesBanda.TPS_CERRADO_V to "TPS cerrado",
    ClavesBanda.TPS_FONDO_V to "TPS a fondo",
    ClavesBanda.TPS_RECORRIDO_MIN_V to "Recorrido mínimo del TPS",
    ClavesBanda.TPS_RUIDO_MAX_V to "Ruido máximo del TPS",
    ClavesBanda.TPS_REPETIBILIDAD_V to "Repetibilidad del TPS",
    ClavesBanda.TPS_LINEALIDAD to "Linealidad del TPS",
    ClavesBanda.REF_5V_V to "Referencia de 5 V",
    ClavesBanda.MASA_V to "Caída de masa",
    ClavesBanda.INYECTOR_OHM to "Resistencia del inyector",
    ClavesBanda.MINIMO_RPM to "Régimen mínimo",
    ClavesBanda.MINIMO_DERIVA_MAX to "Deriva máxima del mínimo",
    ClavesBanda.MINIMO_DESVIACION_MAX to "Variación máxima del mínimo",
    ClavesBanda.RETORNO_VALLE_MIN to "Valle mínimo al retornar",
    ClavesBanda.MOTOR_FRIO_ECT_IAT_DELTA_C to "Diferencia ECT–IAT en frío",
    ClavesBanda.BATERIA_CONTACTO_V to "Batería con contacto",
    ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V to "Batería con contacto y luces",
    ClavesBanda.ARRANQUE_MIN_V to "Voltaje mínimo al arrancar",
    ClavesBanda.CARGA_V to "Voltaje de carga",
    ClavesBanda.SOBRECARGA_MAX_V to "Límite de sobrecarga",
    ClavesBanda.MAP_KOEO_VS_BARO_KPA to "MAP contra barométrica",
    ClavesBanda.CHEQUEO_AJUSTE_DELTA_PP to "Cambio de fuel trim",
    ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V to "Cambio de voltaje",
)
