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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
import com.revscope.feature.workshop.R
import com.revscope.feature.workshop.taller.TarjetaTaller
import java.text.DecimalFormatSymbols
import java.util.Locale

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
        BarraConVolver(stringResource(R.string.workshop_ref_title), acciones.onVolver, subtitulo = estado.modelo ?: estado.vehiculo)
        when {
            estado.cargando -> CargandoReferencias()
            estado.error != null -> ErrorReferencias(estado.error, acciones.onReintentar)
            estado.bandas.isEmpty() -> EmptyState(stringResource(R.string.workshop_ref_empty), Modifier.padding(16.dp))
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
        Text(
            stringResource(R.string.workshop_ref_heading),
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.title,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.workshop_ref_explanation), color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        if (estado.modelo == null) {
            Text(
                stringResource(R.string.workshop_ref_choose_model_hint),
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
    val nombre = nombreBanda(banda.clave)
    val descripcionEditar = stringResource(R.string.workshop_ref_edit_named, nombre)
    val descripcionRestablecer = stringResource(R.string.workshop_ref_reset_named_cd, nombre)
    TarjetaTaller {
        Text(nombre, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        Text(valorBanda(banda, LocalConfiguration.current.locales[0]), color = RevScopeColors.Accent, style = RevScopeType.numeros)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.BookmarkAdded, contentDescription = null, tint = colorOrigen(banda.origen), modifier = Modifier.size(18.dp))
            Text(origen(banda), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { acciones.onEditar(banda.clave) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = descripcionEditar },
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.workshop_modelo_edit), color = RevScopeColors.Accent, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
            }
            if (banda.origen != OrigenBanda.TIPICO) {
                TextButton(
                    onClick = { acciones.onRestablecer(banda.clave) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = descripcionRestablecer },
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.workshop_ref_reset), color = RevScopeColors.TextSecondary, style = RevScopeType.label, modifier = Modifier.padding(start = 6.dp))
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
        title = {
            Text(
                stringResource(R.string.workshop_ref_edit_named, nombreBanda(dialogo.banda.clave)),
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.title,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoLimite(stringResource(R.string.workshop_ref_minimum), dialogo.minimo, acciones.onCambiarMinimo)
                CampoLimite(stringResource(R.string.workshop_ref_maximum), dialogo.maximo, acciones.onCambiarMaximo)
                Text(stringResource(R.string.workshop_ref_unit, dialogo.banda.unidad), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
                dialogo.error?.let { ErrorDialogo(it) }
                Text(stringResource(R.string.workshop_ref_save_note), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = acciones.onGuardar, modifier = Modifier.heightIn(min = 48.dp)) {
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
private fun ErrorDialogo(error: String) {
    val descripcion = stringResource(R.string.workshop_ref_error_cd, error)
    Text(error, color = RevScopeColors.Danger, style = RevScopeType.body, modifier = Modifier.semantics { contentDescription = descripcion })
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

@Composable
internal fun nombreBanda(clave: String): String =
    NOMBRES_BANDAS[clave]?.let { stringResource(it) } ?: clave.lowercase().replace('_', ' ')

internal fun valorBanda(banda: BandaReferencia, locale: Locale): String {
    val minimo = banda.min
    val maximo = banda.max
    return when {
        minimo != null && maximo != null -> "${numero(minimo, locale)}–${numero(maximo, locale)} ${banda.unidad}"
        minimo != null -> "≥ ${numero(minimo, locale)} ${banda.unidad}"
        else -> "≤ ${numero(maximo!!, locale)} ${banda.unidad}"
    }
}

// Se conservan todos los decimales del valor; solo cambia el separador según el idioma.
private fun numero(valor: Double, locale: Locale): String =
    if (valor % 1.0 == 0.0) {
        valor.toInt().toString()
    } else {
        valor.toString().replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    }

@Composable
private fun origen(banda: BandaReferencia): String = when (banda.origen) {
    OrigenBanda.TIPICO -> stringResource(R.string.workshop_ref_origin_typical)
    OrigenBanda.FUENTE -> stringResource(R.string.workshop_modelo_source, banda.fuente)
    OrigenBanda.USUARIO -> stringResource(R.string.workshop_ref_origin_user)
}

private fun colorOrigen(origen: OrigenBanda) = when (origen) {
    OrigenBanda.TIPICO -> RevScopeColors.TextSecondary
    OrigenBanda.FUENTE -> RevScopeColors.Success
    OrigenBanda.USUARIO -> RevScopeColors.Accent
}

private val NOMBRES_BANDAS = mapOf(
    ClavesBanda.TPS_CERRADO_V to R.string.workshop_ref_band_tps_closed,
    ClavesBanda.TPS_FONDO_V to R.string.workshop_ref_band_tps_wide_open,
    ClavesBanda.TPS_RECORRIDO_MIN_V to R.string.workshop_ref_band_tps_min_travel,
    ClavesBanda.TPS_RUIDO_MAX_V to R.string.workshop_ref_band_tps_max_noise,
    ClavesBanda.TPS_REPETIBILIDAD_V to R.string.workshop_ref_band_tps_repeatability,
    ClavesBanda.TPS_LINEALIDAD to R.string.workshop_ref_band_tps_linearity,
    ClavesBanda.REF_5V_V to R.string.workshop_ref_band_ref_5v,
    ClavesBanda.MASA_V to R.string.workshop_ref_band_ground_drop,
    ClavesBanda.INYECTOR_OHM to R.string.workshop_ref_band_injector_ohm,
    ClavesBanda.MINIMO_RPM to R.string.workshop_ref_band_idle_rpm,
    ClavesBanda.MINIMO_DERIVA_MAX to R.string.workshop_ref_band_idle_max_drift,
    ClavesBanda.MINIMO_DESVIACION_MAX to R.string.workshop_ref_band_idle_max_variation,
    ClavesBanda.RETORNO_VALLE_MIN to R.string.workshop_ref_band_return_min_dip,
    ClavesBanda.MOTOR_FRIO_ECT_IAT_DELTA_C to R.string.workshop_ref_band_cold_ect_iat_delta,
    ClavesBanda.BATERIA_CONTACTO_V to R.string.workshop_ref_band_battery_ignition,
    ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V to R.string.workshop_ref_band_battery_ignition_lights,
    ClavesBanda.ARRANQUE_MIN_V to R.string.workshop_ref_band_cranking_min,
    ClavesBanda.CARGA_V to R.string.workshop_ref_band_charging,
    ClavesBanda.SOBRECARGA_MAX_V to R.string.workshop_ref_band_overcharge_max,
    ClavesBanda.MAP_KOEO_VS_BARO_KPA to R.string.workshop_ref_band_map_vs_baro,
    ClavesBanda.CHEQUEO_AJUSTE_DELTA_PP to R.string.workshop_ref_band_fuel_trim_change,
    ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V to R.string.workshop_ref_band_voltage_change,
)
