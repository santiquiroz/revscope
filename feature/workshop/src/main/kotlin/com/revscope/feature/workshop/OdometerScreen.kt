package com.revscope.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.revscope.core.designsystem.AvisoDescartable
import com.revscope.core.designsystem.FilaEtiquetaValor
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.obd.workshop.OdometerChecker
import com.revscope.core.obd.workshop.OdometerVerifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)
private val WarnColor = Color(0xFFFFC107)
private val OkColor = Color(0xFF4CAF50)
private val FailColor = Color(0xFFFF5252)

@Composable
fun OdometerScreen(
    onNavigateBack: () -> Unit,
    viewModel: OdometerViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val soportado by viewModel.odometerSupported.collectAsState()
    val lastCheck by viewModel.lastCheck.collectAsState()
    val historial by viewModel.historial.collectAsState()
    val leyendoAhora by viewModel.leyendoAhora.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()
    val context = LocalContext.current
    val isConnected = connectionState is ConnectionState.Connected

    // Una sola lista: con letra grande, la Column fija dejaba el histórico sin alto.
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgColor).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "encabezado") {
            EncabezadoOdometro(
                puedeExportar = historial.isNotEmpty(),
                onVolver = onNavigateBack,
                onExportar = { viewModel.exportCsv(context) },
            )
        }
        item(key = "leer") {
            Button(
                onClick = viewModel::leerAhora,
                enabled = isConnected && !leyendoAhora,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (leyendoAhora) "Leyendo…" else "Leer ahora")
            }
        }
        if (!isConnected) {
            item(key = "sin_conexion") {
                Text("Conecta el adaptador para leer el odómetro del vehículo", color = TextMutedColor, fontSize = 13.sp)
            }
        }
        mensaje?.let { texto ->
            item(key = "mensaje") {
                AvisoDescartable(texto = texto, onDescartar = viewModel::dismissMensaje, color = WarnColor)
            }
        }
        if (isConnected && soportado == false) {
            item(key = "no_soportado") { NoSoportadoCard() }
        }
        odometroItems(lastCheck, historial)
    }
}

@Composable
private fun EncabezadoOdometro(puedeExportar: Boolean, onVolver: () -> Unit, onExportar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onVolver) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextColor)
        }
        Text(
            "Verificación de kilometraje",
            color = TextColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onExportar, enabled = puedeExportar) {
            Icon(Icons.Default.Download, "Exportar CSV", tint = AccentColor)
        }
    }
}

private fun LazyListScope.odometroItems(
    lastCheck: OdometerChecker.Result?,
    historial: List<OdometerVerifier.Reading>,
) {
    val ultima = lastCheck?.reading ?: historial.lastOrNull()
    if (ultima == null) {
        item(key = "vacio") { EmptyState() }
        return
    }
    item(key = "ultima") { UltimaLecturaCard(ultima, lastCheck?.diagnosis) }
    item(key = "titulo_historico") {
        Text("Histórico", color = TextMutedColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
    items(historial.asReversed()) { lectura -> HistorialRow(lectura) }
}

@Composable
private fun EmptyState() {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Aún no hay lecturas del odómetro. Conecta el adaptador y toca \"Leer ahora\".",
            color = TextMutedColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun NoSoportadoCard() {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Odómetro no disponible por OBD",
                color = WarnColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Tu vehículo no expone el PID estándar 01 A6 — común en motos y vehículos anteriores a 2015. " +
                    "Usa el escáner Mode 22 (Taller → Escáner avanzado) para buscar el DID propietario del fabricante.",
                color = TextMutedColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun UltimaLecturaCard(ultima: OdometerVerifier.Reading, diagnosis: DiagnosticRules.Diagnosis?) {
    val fecha = formatFecha(ultima.epochMs)
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Última lectura ECU", color = TextMutedColor, fontSize = 12.sp)
            Text(
                "${ultima.km.roundToLong()} km",
                color = TextColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(fecha, color = TextMutedColor, fontSize = 11.sp)
            diagnosis?.let {
                Spacer(Modifier.height(8.dp))
                EstadoRow(it)
            }
        }
    }
}

@Composable
private fun EstadoRow(d: DiagnosticRules.Diagnosis) {
    val color = nivelColor(d.nivel)
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).background(color, CircleShape))
        Column(Modifier.padding(start = 10.dp)) {
            Text(d.titulo, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(d.causaProbable, color = TextMutedColor, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HistorialRow(lectura: OdometerVerifier.Reading) {
    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        FilaEtiquetaValor(
            etiqueta = formatFecha(lectura.epochMs),
            valor = "${lectura.km.roundToLong()} km",
            colorValor = TextColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

private fun nivelColor(nivel: DiagnosticRules.Nivel): Color = when (nivel) {
    DiagnosticRules.Nivel.OK -> OkColor
    DiagnosticRules.Nivel.ATENCION -> WarnColor
    DiagnosticRules.Nivel.FALLA -> FailColor
}

private fun formatFecha(epochMs: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es")).format(Date(epochMs))
