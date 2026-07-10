package com.revscope.feature.workshop

import android.content.Context
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.common.export.CsvShare
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.workshop.DiagnosticRules
import kotlinx.coroutines.launch

private val AccentColor = Color(0xFFE8FF00)
private val SurfaceColor = Color(0xFF12121A)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)

private data class MixtureRow(
    val pid: String,
    val label: String,
    val diagnose: ((Double, Map<String, ObdReading>) -> DiagnosticRules.Diagnosis?)? = null,
)

private val ROWS = listOf(
    MixtureRow("06", "Fuel trim corto B1"),
    MixtureRow("07", "Fuel trim largo B1", { v, _ -> DiagnosticRules.evaluarFuelTrimLargo(v) }),
    MixtureRow("08", "Fuel trim corto B2"),
    MixtureRow("09", "Fuel trim largo B2", { v, _ -> DiagnosticRules.evaluarFuelTrimLargo(v) }),
    MixtureRow("14", "Sensor O2 B1S1"),
    MixtureRow("15", "Sensor O2 B1S2"),
    MixtureRow("18", "Sensor O2 B2S1"),
    MixtureRow("19", "Sensor O2 B2S2"),
    MixtureRow("44", "Lambda comandado"),
    MixtureRow("10", "Flujo de aire (MAF)"),
    MixtureRow("0B", "Presión múltiple (MAP)"),
    MixtureRow("0A", "Presión de combustible"),
    MixtureRow("0E", "Avance de encendido"),
    MixtureRow("2E", "Purga EVAP"),
    MixtureRow("3C", "Temp catalizador"),
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
    val visibleRows = ROWS.filter { readings[it.pid] != null }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextColor)
            }
            Text(
                "Mezcla y combustión",
                color = TextColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { scope.launch { exportMixtureSnapshot(context, visibleRows, readings) } },
                enabled = visibleRows.isNotEmpty(),
            ) {
                Icon(Icons.Default.Download, contentDescription = "Exportar CSV", tint = AccentColor)
            }
        }
        Text(
            "Los valores se interpretan en tiempo real. Motor encendido para ver la mezcla trabajar.",
            color = TextMutedColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (visibleRows.isEmpty()) {
            Text(
                "Esperando datos del vehículo… (requiere conexión y motor encendido)",
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleRows) { row -> MixtureRowCard(row, readings, viewModel) }
            }
        }
    }
}

@Composable
private fun MixtureRowCard(
    row: MixtureRow,
    readings: Map<String, ObdReading>,
    viewModel: LiveMixtureViewModel,
) {
    val reading = readings[row.pid] ?: return
    val definition = viewModel.definition(row.pid)
    val diagnosis = row.diagnose?.invoke(reading.value, readings)

    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.label, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "%.1f %s".format(reading.value, reading.unit),
                    color = AccentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (definition != null) {
                val range = definition.max - definition.min
                val progress = if (range != 0.0) {
                    ((reading.value - definition.min) / range).toFloat().coerceIn(0f, 1f)
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
    visibleRows: List<MixtureRow>,
    readings: Map<String, ObdReading>,
) {
    if (visibleRows.isEmpty()) return
    CsvShare.shareCsv(
        context = context,
        tipo = "mezcla-snapshot",
        header = listOf("pid", "nombre", "valor", "unidad", "diagnostico"),
        rows = visibleRows.asSequence().mapNotNull { row -> mixtureSnapshotRow(row, readings) },
    )
}

private fun mixtureSnapshotRow(row: MixtureRow, readings: Map<String, ObdReading>): List<Any?>? {
    val reading = readings[row.pid] ?: return null
    val diagnosis = row.diagnose?.invoke(reading.value, readings)
    return listOf(row.pid, row.label, reading.value, reading.unit, diagnosis?.titulo ?: "")
}
