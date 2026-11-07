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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.revscope.core.obd.diagnostics.ModuleDiscovery
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
                    Text(stringResource(R.string.settings_diagnostico_mode22_title), color = TextPrimaryColor, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_diagnostico_back), tint = TextPrimaryColor)
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
                    Text(stringResource(R.string.settings_diagnostico_no_connection), color = DangerColor, fontSize = 13.sp)
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
                SelectorRangos(vm.presetRanges, selectedRange) { selectedRange = it }
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
            stringResource(R.string.settings_diagnostico_scan_read_only),
            color = TextMutedColor,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PasosEscaneo() {
    Text(
        stringResource(R.string.settings_diagnostico_scan_steps),
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
            Text(if (discovering) stringResource(R.string.settings_diagnostico_discovering_modules) else stringResource(R.string.settings_diagnostico_discover_modules), color = TextPrimaryColor)
        }
        if (noSoportado) {
            Text(
                stringResource(R.string.settings_diagnostico_no_support),
                color = DangerColor,
                fontSize = 12.sp,
            )
        }
        if (modules.isNotEmpty()) {
            Text(stringResource(R.string.settings_diagnostico_modules_responded), color = TextMutedColor, fontSize = 12.sp)
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
                append(stringResource(R.string.settings_diagnostico_target_scan, ""))
                withStyle(SpanStyle(color = AccentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)) {
                    append(header)
                }
            },
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        TextButton(onClick = onVolverAEcu) {
            Text(stringResource(R.string.settings_diagnostico_back_to_ecu), color = AccentColor, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorRangos(
    rangos: List<Mode22ScannerViewModel.DidRange>,
    seleccionado: Mode22ScannerViewModel.DidRange,
    onElegir: (Mode22ScannerViewModel.DidRange) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        rangos.forEach { rango ->
            ChipSeleccion(texto = etiquetaRango(rango), seleccionado = rango == seleccionado, onClick = { onElegir(rango) })
        }
    }
}

@Composable
private fun etiquetaRango(rango: Mode22ScannerViewModel.DidRange): String =
    rango.descriptionRes?.let { stringResource(it, rango.label) } ?: rango.label

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
                    escaneando -> stringResource(R.string.settings_diagnostico_stop_scan)
                    vigilando -> stringResource(R.string.settings_diagnostico_stop_watch)
                    else -> stringResource(R.string.settings_diagnostico_scan)
                },
            )
        }
        FilledTonalButton(
            onClick = onVigilar,
            enabled = puedeVigilar,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceHighColor, contentColor = TextPrimaryColor),
        ) { Text(stringResource(R.string.settings_diagnostico_watch)) }
        OutlinedButton(onClick = onLimpiar, enabled = puedeLimpiar) { Text(stringResource(R.string.settings_diagnostico_clear)) }
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
                stringResource(R.string.settings_diagnostico_progress, state.current, state.total, pluralStringResource(R.plurals.settings_mode22_responses, respuestas, respuestas)),
                color = TextMutedColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Mode22ScannerViewModel.ScannerState.Watching -> Text(
            stringResource(R.string.settings_diagnostico_watching, respuestas),
            color = AccentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Mode22ScannerViewModel.ScannerState.Idle -> if (respuestas > 0) {
            Text(stringResource(R.string.settings_diagnostico_response_count, respuestas), color = TextMutedColor, fontSize = 12.sp)
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
                stringResource(R.string.settings_diagnostico_did, hit.did),
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
                    stringResource(R.string.settings_diagnostico_before, it),
                    color = TextMutedColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (hit.changedDuringWatch) {
            Text(
                stringResource(R.string.settings_diagnostico_changed),
                color = SuccessColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
