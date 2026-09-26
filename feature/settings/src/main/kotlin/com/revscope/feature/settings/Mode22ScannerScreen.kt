package com.revscope.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.ChipSeleccion
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        containerColor = BgColor,
    ) { innerPadding ->
        val protocolSupport by vm.protocolSupport.collectAsState()
        val modules by vm.modules.collectAsState()
        val discovering by vm.discovering.collectAsState()
        val targetHeader by vm.targetHeader.collectAsState()
        val conectado = connectionState is ConnectionState.Connected
        val scanning = state is Mode22ScannerViewModel.ScannerState.Scanning
        val watching = state is Mode22ScannerViewModel.ScannerState.Watching

        // Una sola lista: con letra grande, una Column fija dejaba la lista de resultados en alto 0.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "aviso") { AvisoSoloLectura() }
            if (!conectado) {
                item(key = "sin_conexion") {
                    Text("Conecta el adaptador primero (moto encendida).", color = DangerColor, fontSize = 13.sp)
                }
            }
            item(key = "pasos") { PasosEscaneo() }
            item(key = "modulos") {
                ModulosDescubiertos(
                    modules = modules,
                    targetHeader = targetHeader,
                    discovering = discovering,
                    puedeDescubrir = conectado && !discovering,
                    noSoportado = protocolSupport == Mode22ScannerViewModel.ProtocolSupport.UNSUPPORTED,
                    onDescubrir = { vm.discoverModules(connectionVm) },
                    onElegir = vm::selectTarget,
                )
            }
            targetHeader?.let { header ->
                item(key = "objetivo") { FilaObjetivo(header, onVolverAEcu = { vm.selectTarget(null) }) }
            }
            item(key = "rangos") {
                SelectorRangos(vm.presetRanges.map { it.label }, selectedRange.label) { etiqueta ->
                    selectedRange = vm.presetRanges.first { it.label == etiqueta }
                }
            }
            item(key = "acciones") {
                Mode22Acciones(
                    escaneando = scanning,
                    vigilando = watching,
                    puedeEscanear = conectado || scanning || watching,
                    puedeVigilar = hits.isNotEmpty() && state is Mode22ScannerViewModel.ScannerState.Idle && conectado,
                    puedeLimpiar = hits.isNotEmpty(),
                    onEscanearODetener = {
                        if (scanning || watching) vm.stop() else vm.startScan(connectionVm, selectedRange)
                    },
                    onVigilar = { vm.startWatch(connectionVm) },
                    onLimpiar = vm::clearHits,
                )
            }
            item(key = "progreso") { ProgresoEscaneo(state, hits.size) }
            items(hits.sortedByDescending { it.changedDuringWatch }, key = { it.did }) { hit ->
                HitRow(hit)
            }
        }
    }
}

@Composable
private fun AvisoSoloLectura() {
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
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PasosEscaneo() {
    Text(
        "1. Escanea un rango con el vehículo encendido.\n" +
            "2. Pulsa Vigilar y cambia el modo de manejo: el identificador que " +
            "cambie de valor en ese momento es el del modo.\n" +
            "También puedes descubrir módulos y dirigir el escaneo a uno (p. ej. " +
            "buscar un DID de un subsistema de carrocería).",
        color = TextMutedColor,
        fontSize = 12.sp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModulosDescubiertos(
    modules: List<ModuleDiscovery.ProbeResult>,
    targetHeader: String?,
    discovering: Boolean,
    puedeDescubrir: Boolean,
    noSoportado: Boolean,
    onDescubrir: () -> Unit,
    onElegir: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onDescubrir,
            enabled = puedeDescubrir,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
        ) {
            Text(if (discovering) "Buscando módulos…" else "Descubrir módulos", color = TextPrimaryColor)
        }
        if (noSoportado) {
            Text(
                "Tu vehículo no usa CAN de 11 bits con este adaptador, así que el " +
                    "descubrimiento de módulos de carrocería no está disponible. El escaneo " +
                    "de la ECU de motor sí funciona.",
                color = DangerColor,
                fontSize = 12.sp,
            )
        }
        if (modules.isNotEmpty()) {
            Text("Módulos que respondieron — toca uno para dirigir el escaneo:", color = TextMutedColor, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modules.forEach { m ->
                    val selected = targetHeader == m.requestHeader
                    ChipSeleccion(
                        texto = m.requestHeader + (m.replyHeader?.let { " → $it" } ?: ""),
                        seleccionado = selected,
                        onClick = { onElegir(if (selected) null else m.requestHeader) },
                        monoespaciado = true,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilaObjetivo(header: String, onVolverAEcu: () -> Unit) {
    // FlowRow: con letra grande el botón baja de línea en vez de apretar la etiqueta palabra por palabra.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                append("Objetivo del escaneo: ")
                withStyle(SpanStyle(color = AccentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)) {
                    append(header)
                }
            },
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        TextButton(onClick = onVolverAEcu) {
            Text("Volver a la ECU", color = AccentColor, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorRangos(etiquetas: List<String>, seleccionada: String, onElegir: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        etiquetas.forEach { etiqueta ->
            ChipSeleccion(texto = etiqueta, seleccionado = etiqueta == seleccionada, onClick = { onElegir(etiqueta) })
        }
    }
}

/** Una sola acción principal (escanear o detener); vigilar y limpiar son secundarias. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Mode22Acciones(
    escaneando: Boolean,
    vigilando: Boolean,
    puedeEscanear: Boolean,
    puedeVigilar: Boolean,
    puedeLimpiar: Boolean,
    onEscanearODetener: () -> Unit,
    onVigilar: () -> Unit,
    onLimpiar: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onEscanearODetener,
            enabled = puedeEscanear,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = BgColor),
        ) {
            Text(
                when {
                    escaneando -> "Detener escaneo"
                    vigilando -> "Detener vigilancia"
                    else -> "Escanear"
                },
            )
        }
        FilledTonalButton(
            onClick = onVigilar,
            enabled = puedeVigilar,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceHighColor, contentColor = TextPrimaryColor),
        ) { Text("Vigilar") }
        OutlinedButton(onClick = onLimpiar, enabled = puedeLimpiar) { Text("Limpiar") }
    }
}

@Composable
private fun ProgresoEscaneo(state: Mode22ScannerViewModel.ScannerState, respuestas: Int) {
    when (state) {
        is Mode22ScannerViewModel.ScannerState.Scanning -> Column {
            LinearProgressIndicator(
                progress = { state.current.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
                color = AccentColor,
                trackColor = SurfaceHighColor,
            )
            Text(
                "${state.current}/${state.total} — $respuestas respuestas",
                color = TextMutedColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Mode22ScannerViewModel.ScannerState.Watching -> Text(
            "Vigilando $respuestas identificadores — cambia el modo de manejo ahora",
            color = AccentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Mode22ScannerViewModel.ScannerState.Idle -> if (respuestas > 0) {
            Text("$respuestas identificadores con respuesta", color = TextMutedColor, fontSize = 12.sp)
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
