package com.revscope.feature.workshop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.viewmodel.ConnectionViewModel

private val AccentColor = Color(0xFFE8FF00)
private val SurfaceColor = Color(0xFF12121A)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)

private data class WorkshopTool(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val needsConnection: Boolean,
    val onOpen: () -> Unit,
)

private data class WorkshopSection(
    val title: String,
    val tools: List<WorkshopTool>,
)

@Composable
fun WorkshopScreen(
    connectionVm: ConnectionViewModel,
    onOpenAlDia: () -> Unit,
    onOpenHealthCheck: () -> Unit,
    onOpenDtc: () -> Unit,
    onOpenLiveMixture: () -> Unit,
    onOpenSensors: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenGearAnalyzer: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenO2Wave: () -> Unit,
    onOpenMode06: () -> Unit,
) {
    val connState by connectionVm.connectionState.collectAsState()
    val isConnected = connState is ConnectionState.Connected

    val sections = buildWorkshopSections(
        onOpenAlDia, onOpenHealthCheck, onOpenDtc, onOpenLiveMixture,
        onOpenSensors, onOpenScanner, onOpenGearAnalyzer, onOpenProfiles, onOpenMaintenance,
        onOpenO2Wave, onOpenMode06,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Taller", color = TextColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (!isConnected) {
                Text(
                    "Conecta el adaptador para usar las herramientas de diagnóstico",
                    color = TextMutedColor, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        sections.forEach { section ->
            item { SectionHeader(section.title) }
            items(section.tools) { tool ->
                val enabled = isConnected || !tool.needsConnection
                ToolCard(tool, enabled)
            }
        }
    }
}

private fun buildWorkshopSections(
    onOpenAlDia: () -> Unit,
    onOpenHealthCheck: () -> Unit,
    onOpenDtc: () -> Unit,
    onOpenLiveMixture: () -> Unit,
    onOpenSensors: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenGearAnalyzer: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenO2Wave: () -> Unit,
    onOpenMode06: () -> Unit,
): List<WorkshopSection> = listOf(
    WorkshopSection(
        "Estado",
        listOf(
            WorkshopTool(Icons.Default.Verified, "Vehículo al día",
                "SOAT, tecnomecánica, pico y placa, multas y todo riesgo", false, onOpenAlDia),
            WorkshopTool(Icons.Default.MonitorHeart, "Chequeo de salud",
                "Escaneo completo con diagnóstico en español — DTCs, readiness, mezcla, batería", false, onOpenHealthCheck),
        ),
    ),
    WorkshopSection(
        "Diagnóstico",
        listOf(
            WorkshopTool(Icons.Default.BugReport, "Códigos de falla (DTC)",
                "Leer, explicar con IA y borrar códigos de error", true, onOpenDtc),
            WorkshopTool(Icons.Default.Science, "Mezcla y combustión",
                "Trims, O2, lambda y MAF interpretados en vivo", true, onOpenLiveMixture),
            WorkshopTool(Icons.Default.Timeline, "Gráficas de sensores",
                "Curvas en tiempo real de cualquier PID", true, onOpenSensors),
            WorkshopTool(Icons.Default.Search, "Escáner avanzado (Mode 22)",
                "Descubrir PIDs propietarios del fabricante", true, onOpenScanner),
            WorkshopTool(Icons.AutoMirrored.Filled.ShowChart, "Onda sensor O2",
                "Gráfica en vivo del voltaje del sensor de oxígeno", true, onOpenO2Wave),
            WorkshopTool(Icons.AutoMirrored.Filled.FactCheck, "Resultados a bordo (Mode 06)",
                "Pruebas de monitoreo internas del fabricante", true, onOpenMode06),
        ),
    ),
    WorkshopSection(
        "Vehículo",
        listOf(
            WorkshopTool(Icons.Default.Settings, "Analizador de marchas",
                "Calibrar la relación RPM/velocidad por marcha", true, onOpenGearAnalyzer),
            WorkshopTool(Icons.Default.DirectionsCar, "Perfiles de vehículo",
                "Vehículos guardados, línea roja y VIN", false, onOpenProfiles),
            WorkshopTool(Icons.Default.Build, "Mantenimiento",
                "Aceite, llantas, batería y otros ítems por kilometraje", false, onOpenMaintenance),
        ),
    ),
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        color = TextMutedColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ToolCard(tool: WorkshopTool, enabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = tool.onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                tool.icon,
                contentDescription = null,
                tint = if (enabled) AccentColor else TextMutedColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    tool.title,
                    color = if (enabled) TextColor else TextMutedColor,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (enabled) tool.description else "Requiere conexión",
                    color = TextMutedColor, fontSize = 12.sp,
                )
            }
        }
    }
}
