package com.revscope.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.obd.workshop.DiagnosticRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AccentColor = Color(0xFFE8FF00)
private val SurfaceColor = Color(0xFF12121A)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)
private val ErrorColor = Color(0xFFFF5252)

@Composable
fun HealthCheckScreen(
    onNavigateBack: () -> Unit,
    viewModel: HealthCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextColor)
            }
            Text(
                "Chequeo de salud",
                color = TextColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (state is HealthCheckViewModel.UiState.Done) {
                IconButton(onClick = { viewModel.share(context) }) {
                    Icon(Icons.Default.PhotoCamera, "Compartir informe", tint = AccentColor)
                }
            }
        }

        Button(
            onClick = viewModel::runHealthCheck,
            enabled = state !is HealthCheckViewModel.UiState.Running,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            Text(if (state is HealthCheckViewModel.UiState.Running) "Escaneando…" else "Escanear ahora")
        }

        HealthCheckContent(state)
    }
}

@Composable
private fun HealthCheckContent(state: HealthCheckViewModel.UiState) {
    when (state) {
        is HealthCheckViewModel.UiState.Idle -> Text(
            "Un toque y RevScope revisa códigos de falla, readiness para la tecnomecánica, " +
                "mezcla, sensor O2, batería y temperatura.",
            color = TextMutedColor,
            fontSize = 13.sp,
        )
        is HealthCheckViewModel.UiState.Running -> RunningIndicator(state.paso)
        is HealthCheckViewModel.UiState.Error -> Text(state.mensaje, color = ErrorColor, fontSize = 14.sp)
        is HealthCheckViewModel.UiState.Done -> DoneResults(state)
    }
}

@Composable
private fun RunningIndicator(paso: String) {
    LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentColor)
    Text(paso, color = TextMutedColor, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DoneResults(done: HealthCheckViewModel.UiState.Done) {
    val fecha = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es")).format(Date(done.timestamp))
    Text(
        "Último chequeo: $fecha",
        color = TextMutedColor,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(done.items) { item -> DiagnosisRow(item) }
    }
}

@Composable
private fun DiagnosisRow(d: DiagnosticRules.Diagnosis) {
    val color = when (d.nivel) {
        DiagnosticRules.Nivel.OK -> Color(0xFF4CAF50)
        DiagnosticRules.Nivel.ATENCION -> Color(0xFFFFC107)
        DiagnosticRules.Nivel.FALLA -> ErrorColor
    }
    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).background(color, CircleShape))
            Column(Modifier.padding(start = 12.dp)) {
                Text(d.titulo, color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(d.causaProbable, color = TextMutedColor, fontSize = 12.sp)
                Text(d.area, color = TextMutedColor, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
