package com.revscope.feature.workshop

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.FilaEtiquetaValor
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.common.export.CsvShare
import com.revscope.core.common.i18n.AndroidStringResolver
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.taller.pid.DisponibilidadPid
import kotlinx.coroutines.launch

private val AccentColor = Color(0xFFE8FF00)
private val SurfaceColor = Color(0xFF12121A)
private val TextColor = Color(0xFFE6E8F0)

private data class MixtureRow(
    val pid: String,
    @StringRes val labelRes: Int,
    val diagnose: ((Double, Map<String, ObdReading>, StringResolver) -> DiagnosticRules.Diagnosis?)? = null,
)

private val ROWS = listOf(
    MixtureRow("06", R.string.workshop_mixture_fuel_trim_short_b1),
    MixtureRow("07", R.string.workshop_mixture_fuel_trim_long_b1) { v, _, r -> DiagnosticRules.evaluarFuelTrimLargo(v, r) },
    MixtureRow("08", R.string.workshop_mixture_fuel_trim_short_b2),
    MixtureRow("09", R.string.workshop_mixture_fuel_trim_long_b2) { v, _, r -> DiagnosticRules.evaluarFuelTrimLargo(v, r) },
    MixtureRow("14", R.string.workshop_mixture_o2_b1s1),
    MixtureRow("15", R.string.workshop_mixture_o2_b1s2),
    MixtureRow("18", R.string.workshop_mixture_o2_b2s1),
    MixtureRow("19", R.string.workshop_mixture_o2_b2s2),
    MixtureRow("44", R.string.workshop_mixture_commanded_lambda),
    MixtureRow("10", R.string.workshop_mixture_airflow_maf),
    MixtureRow("0B", R.string.workshop_mixture_manifold_pressure_map),
    MixtureRow("0A", R.string.workshop_mixture_fuel_pressure),
    MixtureRow("0E", R.string.workshop_mixture_ignition_timing),
    MixtureRow("2E", R.string.workshop_mixture_evap_purge),
    MixtureRow("3C", R.string.workshop_mixture_catalyst_temperature),
)

/** PIDs this screen displays — used by the ViewModel to filter out unrelated dashboard traffic. */
internal val MIXTURE_ROW_PIDS: Set<String> = ROWS.map { it.pid }.toSet()

@Composable
fun LiveMixtureScreen(
    onNavigateBack: () -> Unit,
    viewModel: LiveMixtureViewModel = hiltViewModel(),
) {
    DisposableEffect(Unit) {
        viewModel.setWorkshopMode(true)
        onDispose { viewModel.setWorkshopMode(false) }
    }

    val readings by viewModel.readings.collectAsState()
    val capacidades by viewModel.capacidadesEcu.collectAsState()
    val disponibilidadActual = remember(capacidades) { viewModel::disponibilidad }
    val labels = mixtureLabels()
    val exportables = ROWS.filter { row ->
        readings[row.pid] != null && disponibilidadActual(row.pid, labels.getValue(row.pid)).estado != EstadoSoporte.NoSoportado
    }
    val context = LocalContext.current
    val resolver = remember(context) { AndroidStringResolver(context) }
    val scope = rememberCoroutineScope()

    LiveMixtureContent(
        readings = readings,
        definition = viewModel::definition,
        disponibilidad = disponibilidadActual,
        labels = labels,
        onNavigateBack = onNavigateBack,
        onExport = { scope.launch { exportMixtureSnapshot(context, resolver, exportables, readings) } },
    )
}

@Composable
internal fun LiveMixtureContent(
    readings: Map<String, ObdReading>,
    definition: (String) -> PidDefinition?,
    disponibilidad: (String, String) -> DisponibilidadPid,
    labels: Map<String, String> = mixtureLabels(),
    onNavigateBack: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    val exportables = ROWS.filter { row ->
        readings[row.pid] != null && disponibilidad(row.pid, labels.getValue(row.pid)).estado != EstadoSoporte.NoSoportado
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.revscope.core.designsystem.R.string.ds_back), tint = TextColor)
            }
            Text(
                stringResource(R.string.workshop_mixture_title),
                color = TextColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onExport,
                enabled = exportables.isNotEmpty(),
            ) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.workshop_mixture_export_csv), tint = AccentColor)
            }
        }
        Text(
            stringResource(R.string.workshop_mixture_description),
            color = RevScopeColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ROWS) { row -> MixtureRowCard(row, labels.getValue(row.pid), readings, definition, disponibilidad) }
        }
    }
}

@Composable
private fun mixtureLabels(): Map<String, String> =
    ROWS.associate { row -> row.pid to stringResource(row.labelRes) }

@Composable
private fun MixtureRowCard(
    row: MixtureRow,
    label: String,
    readings: Map<String, ObdReading>,
    definition: (String) -> PidDefinition?,
    disponibilidad: (String, String) -> DisponibilidadPid,
) {
    val disponibilidadPid = disponibilidad(row.pid, label.lowercase())
    val reading = readings[row.pid].takeUnless { disponibilidadPid.estado == EstadoSoporte.NoSoportado }
    val definicion = definition(row.pid)
    val context = LocalContext.current
    val resolver = remember(context) { AndroidStringResolver(context) }
    val diagnosis = reading?.let { row.diagnose?.invoke(it.value, readings, resolver) }
    val locale = LocalConfiguration.current.locales[0]

    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (reading == null) {
                Text(label, color = TextColor, style = RevScopeType.label)
                Text(
                    stringResource(
                        if (disponibilidadPid.estado == EstadoSoporte.NoSoportado) {
                            R.string.workshop_mixture_unavailable_ecu
                        } else {
                            R.string.workshop_mixture_waiting_reading
                        },
                    ),
                    color = RevScopeColors.TextSecondary,
                    style = RevScopeType.bodySmall,
                )
                when (disponibilidadPid.estado) {
                    EstadoSoporte.NoSoportado -> Text(
                        stringResource(R.string.workshop_mixture_unsupported_reason, label.lowercase(), row.pid),
                        color = RevScopeColors.TextSecondary,
                        style = RevScopeType.bodySmall,
                    )
                    EstadoSoporte.Desconocido -> Text(
                        stringResource(R.string.workshop_mixture_unknown_pids),
                        color = RevScopeColors.TextSecondary,
                        style = RevScopeType.bodySmall,
                    )
                    EstadoSoporte.Soportado -> Unit
                }
            } else {
                FilaEtiquetaValor(
                    etiqueta = label,
                    valor = stringResource(
                        R.string.workshop_mixture_value_unit,
                        LocaleNumbers.decimal(reading.value, 1, locale),
                        reading.unit,
                    ),
                    colorEtiqueta = TextColor,
                    colorValor = AccentColor,
                    estiloEtiqueta = RevScopeType.label,
                )
            }
            if (definicion != null && reading != null) {
                val range = definicion.max - definicion.min
                val progress = if (range != 0.0) {
                    ((reading.value - definicion.min) / range).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = AccentColor,
                )
            }
            if (diagnosis != null) {
                DiagnosisChip(diagnosis)
            }
        }
    }
}

/** Also reused by O2WaveScreen — file-private visibility would hide it outside this file. */
@Composable
internal fun DiagnosisChip(diagnosis: DiagnosticRules.Diagnosis) {
    val color = when (diagnosis.nivel) {
        DiagnosticRules.Nivel.OK -> Color(0xFF4CAF50)
        DiagnosticRules.Nivel.ATENCION -> Color(0xFFFFC107)
        DiagnosticRules.Nivel.FALLA -> Color(0xFFFF5252)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            diagnosis.titulo,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private suspend fun exportMixtureSnapshot(
    context: Context,
    resolver: StringResolver,
    visibleRows: List<MixtureRow>,
    readings: Map<String, ObdReading>,
) {
    if (visibleRows.isEmpty()) return
    CsvShare.shareCsv(
        context = context,
        tipo = "mezcla-snapshot",
        header = listOf("pid", "nombre", "valor", "unidad", "diagnostico"),
        rows = visibleRows.asSequence().mapNotNull { row -> mixtureSnapshotRow(row, readings, resolver) },
        resolver = resolver,
    )
}

private fun mixtureSnapshotRow(
    row: MixtureRow,
    readings: Map<String, ObdReading>,
    resolver: StringResolver,
): List<Any?>? {
    val reading = readings[row.pid] ?: return null
    val diagnosis = row.diagnose?.invoke(reading.value, readings, resolver)
    return listOf(row.pid, resolver.get(row.labelRes), reading.value, reading.unit, diagnosis?.titulo ?: "")
}
