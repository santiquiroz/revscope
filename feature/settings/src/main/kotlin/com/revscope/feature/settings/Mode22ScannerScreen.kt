package com.revscope.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.viewmodel.ConnectionViewModel

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val SuccessColor = Color(0xFF3DFF8E)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)
private val DangerColor = Color(0xFFFF3D5A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Mode22ScannerScreen(
    onNavigateBack: () -> Unit = {},
    connectionVm: ConnectionViewModel = hiltViewModel(),
    vm: Mode22ScannerViewModel = hiltViewModel(),
) {
    val connectionState by connectionVm.connectionState.collectAsState()
    val state by vm.state.collectAsState()
    val hits by vm.hits.collectAsState()
    var selectedRange by remember { mutableStateOf(vm.presetRanges.first()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Escáner Modo 22", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        containerColor = BgColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceHighColor, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("🛈", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                Text(
                    "Este escaneo es de SOLO LECTURA: envía peticiones de diagnóstico " +
                        "estándar para leer datos. No escribe, no borra códigos ni modifica " +
                        "nada del vehículo — es seguro. Hazlo con el vehículo detenido.",
                    color = TextMutedColor,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (connectionState !is ConnectionState.Connected) {
                Text(
                    "Conecta el adaptador primero (moto encendida).",
                    color = DangerColor,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "1. Escanea un rango con el vehículo encendido.\n" +
                    "2. Pulsa Vigilar y cambia el modo de manejo: el identificador que " +
                    "cambie de valor en ese momento es el del modo.\n" +
                    "También puedes descubrir módulos y dirigir el escaneo a uno (p. ej. " +
                    "buscar un DID de un subsistema de carrocería).",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))

            val protocolSupport by vm.protocolSupport.collectAsState()
            val modules by vm.modules.collectAsState()
            val discovering by vm.discovering.collectAsState()
            val targetHeader by vm.targetHeader.collectAsState()

            Button(
                onClick = { vm.discoverModules(connectionVm) },
                enabled = connectionState is ConnectionState.Connected && !discovering,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
            ) {
                Text(if (discovering) "Buscando módulos…" else "Descubrir módulos", color = TextPrimaryColor)
            }

            if (protocolSupport == Mode22ScannerViewModel.ProtocolSupport.UNSUPPORTED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tu vehículo no usa CAN de 11 bits con este adaptador, así que el " +
                        "descubrimiento de módulos de carrocería no está disponible. El escaneo " +
                        "de la ECU de motor sí funciona.",
                    color = DangerColor,
                    fontSize = 12.sp,
                )
            }

            if (modules.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Módulos que respondieron — toca uno para dirigir el escaneo:", color = TextMutedColor, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    modules.forEach { m ->
                        val selected = targetHeader == m.requestHeader
                        Text(
                            m.requestHeader + (m.replyHeader?.let { " → $it" } ?: ""),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (selected) BgColor else TextPrimaryColor,
                            modifier = Modifier
                                .background(if (selected) AccentColor else SurfaceHighColor, RoundedCornerShape(16.dp))
                                .clickable { vm.selectTarget(if (selected) null else m.requestHeader) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            targetHeader?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Objetivo del escaneo: ", color = TextMutedColor, fontSize = 12.sp)
                    Text(it, color = AccentColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Volver a la ECU",
                        color = TextMutedColor,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(SurfaceHighColor, RoundedCornerShape(12.dp))
                            .clickable { vm.selectTarget(null) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.presetRanges.forEach { range ->
                    val selected = range == selectedRange
                    Text(
                        range.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) BgColor else TextMutedColor,
                        modifier = Modifier
                            .background(
                                if (selected) AccentColor else SurfaceHighColor,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { selectedRange = range }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val scanning = state is Mode22ScannerViewModel.ScannerState.Scanning
                val watching = state is Mode22ScannerViewModel.ScannerState.Watching
                Button(
                    onClick = {
                        if (scanning || watching) vm.stop()
                        else vm.startScan(connectionVm, selectedRange)
                    },
                    enabled = connectionState is ConnectionState.Connected || scanning || watching,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                ) {
                    Text(
                        when {
                            scanning -> "Detener escaneo"
                            watching -> "Detener vigilancia"
                            else -> "Escanear"
                        },
                        color = BgColor,
                    )
                }
                Button(
                    onClick = { vm.startWatch(connectionVm) },
                    enabled = hits.isNotEmpty() && state is Mode22ScannerViewModel.ScannerState.Idle &&
                        connectionState is ConnectionState.Connected,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
                ) {
                    Text("Vigilar", color = TextPrimaryColor)
                }
                Button(
                    onClick = { vm.clearHits() },
                    enabled = hits.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
                ) {
                    Text("Limpiar", color = TextMutedColor)
                }
            }
            Spacer(Modifier.height(12.dp))

            when (val s = state) {
                is Mode22ScannerViewModel.ScannerState.Scanning -> {
                    LinearProgressIndicator(
                        progress = { s.current.toFloat() / s.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentColor,
                        trackColor = SurfaceHighColor,
                    )
                    Text(
                        "${s.current}/${s.total} — ${hits.size} respuestas",
                        color = TextMutedColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Mode22ScannerViewModel.ScannerState.Watching -> Text(
                    "Vigilando ${hits.size} identificadores — cambia el modo de manejo ahora",
                    color = AccentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Mode22ScannerViewModel.ScannerState.Idle -> if (hits.isNotEmpty()) {
                    Text("${hits.size} identificadores con respuesta", color = TextMutedColor, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(hits.sortedByDescending { it.changedDuringWatch }, key = { it.did }) { hit ->
                    HitRow(hit)
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: Mode22ScannerViewModel.ScanHit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (hit.changedDuringWatch) SuccessColor.copy(alpha = 0.12f) else SurfaceColor,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "DID ${hit.did}",
                color = if (hit.changedDuringWatch) SuccessColor else TextPrimaryColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                hit.value,
                color = TextMutedColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            hit.previousValue?.let {
                Text(
                    "antes: $it",
                    color = TextMutedColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (hit.changedDuringWatch) {
            Text(
                "CAMBIÓ",
                color = SuccessColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
