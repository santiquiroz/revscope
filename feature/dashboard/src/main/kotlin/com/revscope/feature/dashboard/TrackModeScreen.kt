package com.revscope.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.revscope.core.obd.motion.MotionMetricsHub
import com.revscope.core.obd.track.TrackModeEngine
import com.revscope.feature.dashboard.ui.RevScopeColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class TrackModeViewModel @Inject constructor(
    val engine: TrackModeEngine,
    val motionHub: MotionMetricsHub,
    private val ghostClient: com.revscope.core.obd.social.GhostClient,
) : ViewModel() {

    sealed interface GhostAction {
        data object Idle : GhostAction
        data object Working : GhostAction
        data class Message(val text: String) : GhostAction
    }

    private val _ghostAction = kotlinx.coroutines.flow.MutableStateFlow<GhostAction>(GhostAction.Idle)
    val ghostAction: kotlinx.coroutines.flow.StateFlow<GhostAction> = _ghostAction

    private val _nearbyGhosts = kotlinx.coroutines.flow.MutableStateFlow<List<com.revscope.core.obd.social.GhostClient.GhostSummary>>(emptyList())
    val nearbyGhosts: kotlinx.coroutines.flow.StateFlow<List<com.revscope.core.obd.social.GhostClient.GhostSummary>> = _nearbyGhosts

    fun uploadBestLap() {
        val best = engine.bestLapForUpload() ?: run {
            _ghostAction.value = GhostAction.Message("Aún no tienes una vuelta para subir")
            return
        }
        viewModelScope.launch {
            _ghostAction.value = GhostAction.Working
            val id = ghostClient.upload(best.finishLat, best.finishLon, best.timeMs, best.points)
            _ghostAction.value = GhostAction.Message(
                if (id != null) "Fantasma subido ✓" else "No se pudo subir (¿servidor configurado?)"
            )
        }
    }

    fun loadNearbyGhosts() {
        val finish = engine.finishLinePoint() ?: return
        viewModelScope.launch {
            _ghostAction.value = GhostAction.Working
            _nearbyGhosts.value = ghostClient.near(finish.first, finish.second)
            _ghostAction.value =
                if (_nearbyGhosts.value.isEmpty()) GhostAction.Message("Sin fantasmas para esta pista")
                else GhostAction.Idle
        }
    }

    fun raceGhost(id: Long) {
        viewModelScope.launch {
            _ghostAction.value = GhostAction.Working
            val points = ghostClient.download(id)
            if (points.size >= 2) {
                engine.setRemoteGhost(points)
                _ghostAction.value = GhostAction.Message("Corriendo contra el fantasma 👻")
            } else {
                _ghostAction.value = GhostAction.Message("No se pudo cargar el fantasma")
            }
            _nearbyGhosts.value = emptyList()
        }
    }

    fun useSelfGhost() {
        engine.useSelfGhost()
        _nearbyGhosts.value = emptyList()
        _ghostAction.value = GhostAction.Message("Fantasma remoto quitado")
    }

    fun clearGhostMessage() { _ghostAction.value = GhostAction.Idle }
}

private fun formatLap(ms: Long): String {
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1000
    val hundredths = (ms % 1000) / 10
    return "%d:%02d.%02d".format(minutes, seconds, hundredths)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackModeScreen(
    onNavigateBack: () -> Unit = {},
    vm: TrackModeViewModel = hiltViewModel(),
) {
    val state by vm.engine.state.collectAsState()
    val motion by vm.motionHub.snapshot.collectAsState()
    val ghostDeltaMs by vm.engine.ghostDeltaMs.collectAsState()

    // Live current-lap clock, ticking locally at 5 Hz
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.lapInProgress) {
        while (state.lapInProgress) {
            nowMs = System.currentTimeMillis()
            delay(200)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modo Pista", color = RevScopeColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RevScopeColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RevScopeColors.Surface),
            )
        },
        containerColor = RevScopeColors.Background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!state.hasGpsFix) {
                Text(
                    "Esperando señal GPS… (conecta el adaptador y muévete un poco)",
                    color = RevScopeColors.TextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (!state.finishLineSet) {
                Text(
                    "Rueda despacio SOBRE la línea de meta y pulsa el botón — " +
                        "la línea queda perpendicular a tu dirección (30 m de ancho).",
                    color = RevScopeColors.TextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.engine.armFinishLine() },
                    enabled = state.hasGpsFix,
                    colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Accent),
                ) {
                    Text("🏁 Marcar línea de meta aquí", color = RevScopeColors.Background, fontWeight = FontWeight.Bold)
                }
            } else {
                // Current lap clock
                val currentLapMs = state.currentLapStartMs?.let { (nowMs - it).coerceAtLeast(0) }
                Text(
                    currentLapMs?.let { formatLap(it) } ?: "— : —",
                    color = RevScopeColors.Accent,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    if (state.lapInProgress) "vuelta ${state.laps.size + 1} en curso"
                    else "cruza la línea para iniciar la vuelta 1",
                    color = RevScopeColors.TextMuted,
                    fontSize = 13.sp,
                )
                // Delta vs tu fantasma (mejor vuelta de la sesión): + rojo = perdiendo
                ghostDeltaMs?.let { delta ->
                    Text(
                        "${if (delta >= 0) "+" else "−"}%.1fs 👻".format(kotlin.math.abs(delta) / 1000.0),
                        color = if (delta >= 0) RevScopeColors.Danger else RevScopeColors.Success,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrackStat("Última", state.laps.lastOrNull()?.let { formatLap(it.timeMs) } ?: "—", Modifier.weight(1f))
                    TrackStat("Mejor", state.bestLapMs?.let { formatLap(it) } ?: "—", Modifier.weight(1f))
                    TrackStat("Vueltas", state.laps.size.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrackStat("G lat", "%.2f".format(motion.gLat), Modifier.weight(1f))
                    TrackStat("G long", "%.2f".format(motion.gLong), Modifier.weight(1f))
                    TrackStat(
                        if (motion.calibrated) "Lean" else "Lean (calibrando…)",
                        "%.0f°".format(motion.leanDeg),
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.laps.reversed(), key = { it.number }) { lap ->
                        val isBest = lap.timeMs == state.bestLapMs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "Vuelta ${lap.number}",
                                color = RevScopeColors.TextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatLap(lap.timeMs),
                                color = if (isBest) RevScopeColors.Success else RevScopeColors.Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                GhostSection(vm, hasBestLap = state.bestLapMs != null)

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.engine.clear() },
                    colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.SurfaceHigh),
                ) {
                    Text("Quitar línea y reiniciar", color = RevScopeColors.TextPrimary)
                }
            }
        }
    }
}

/** Fantasmas remotos: subir tu mejor vuelta y elegir contra quién correr. Opt-in, requiere servidor. */
@Composable
private fun GhostSection(vm: TrackModeViewModel, hasBestLap: Boolean) {
    val action by vm.ghostAction.collectAsState()
    val nearby by vm.nearbyGhosts.collectAsState()

    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { vm.uploadBestLap() },
            enabled = hasBestLap && action !is TrackModeViewModel.GhostAction.Working,
            colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Surface),
            modifier = Modifier.weight(1f),
        ) {
            Text("👻 Subir vuelta", color = RevScopeColors.TextPrimary, fontSize = 13.sp)
        }
        Button(
            onClick = { vm.loadNearbyGhosts() },
            enabled = action !is TrackModeViewModel.GhostAction.Working,
            colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Surface),
            modifier = Modifier.weight(1f),
        ) {
            Text("Retar fantasma", color = RevScopeColors.TextPrimary, fontSize = 13.sp)
        }
    }
    (action as? TrackModeViewModel.GhostAction.Message)?.let {
        Text(it.text, color = RevScopeColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
    nearby.forEach { ghost ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
                .clickable { vm.raceGhost(ghost.id) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("👻 ${ghost.rider}", color = RevScopeColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                formatLap(ghost.timeMs),
                color = RevScopeColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun TrackStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            color = RevScopeColors.Accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(label, color = RevScopeColors.TextMuted, fontSize = 11.sp)
    }
}
