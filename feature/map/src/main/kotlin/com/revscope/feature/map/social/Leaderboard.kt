package com.revscope.feature.map.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.social.RoomClient
import com.revscope.core.obd.telemetry.TripStatsCalculator
import kotlin.math.ceil
import kotlin.math.max

private const val MIN_ETA_SPEED_KMH = 5.0
private const val DEFAULT_ARRIVAL_RADIUS_M = 40.0
private const val METERS_PER_KM = 1_000.0
private const val MINUTES_PER_HOUR = 60.0

private val PanelBg = Color(0xE6121218)
private val AccentYellow = Color(0xFFE8FF00)
private val TextPrimary = Color(0xFFF0F0F8)
private val TextMuted = Color(0xFF6B7089)
private val DangerRed = Color(0xFFFF5252)

/**
 * Cálculo puro de posiciones y ETAs de la sala respecto de un destino compartido. Sin
 * arbitraje de servidor: cada cliente lo computa igual con los mismos datos (spec F3).
 */
object RankingCalc {

    data class Entry(
        val name: String,
        val remainingM: Double,
        val etaMin: Double?,
        val arrived: Boolean,
        val isSelf: Boolean,
    )

    /**
     * [self] es (nombre, punto) — null si todavía no hay posición propia. Orden: llegados
     * primero, en el orden en que aparecen ([self] seguido de [peers]); luego el resto por
     * restante ascendente. Sin velocidad conocida el ETA queda null y esa entry va al fondo
     * de los que no llegaron, incluso si su restante es menor que el de otras con velocidad.
     */
    fun rank(
        self: Pair<String, LiveRouteHolder.RoutePoint>?,
        selfSpeedKmh: Double?,
        peers: Collection<RoomClient.Peer>,
        dest: RoomClient.SharedDest,
        arrivalRadiusM: Double = DEFAULT_ARRIVAL_RADIUS_M,
    ): List<Entry> {
        val entries = riders(self, selfSpeedKmh, peers).map { it.toEntry(dest, arrivalRadiusM) }
        val (arrived, pending) = entries.partition { it.arrived }
        return arrived + pending.sortedWith(compareBy({ it.etaMin == null }, { it.remainingM }))
    }

    private data class Rider(
        val name: String,
        val lat: Double,
        val lon: Double,
        val speedKmh: Double?,
        val isSelf: Boolean,
    )

    private fun riders(
        self: Pair<String, LiveRouteHolder.RoutePoint>?,
        selfSpeedKmh: Double?,
        peers: Collection<RoomClient.Peer>,
    ): List<Rider> = buildList {
        self?.let { (name, point) -> add(Rider(name, point.lat, point.lon, selfSpeedKmh, isSelf = true)) }
        peers.forEach { add(Rider(it.rider, it.lat, it.lon, it.speedKmh, isSelf = false)) }
    }

    private fun Rider.toEntry(dest: RoomClient.SharedDest, arrivalRadiusM: Double): Entry {
        val remainingM = TripStatsCalculator.haversineMeters(lat, lon, dest.lat, dest.lon)
        return Entry(
            name = name,
            remainingM = remainingM,
            etaMin = speedKmh?.let { etaMinutes(remainingM, it) },
            arrived = remainingM < arrivalRadiusM,
            isSelf = isSelf,
        )
    }

    private fun etaMinutes(remainingM: Double, speedKmh: Double): Double =
        (remainingM / METERS_PER_KM) / max(speedKmh, MIN_ETA_SPEED_KMH) * MINUTES_PER_HOUR
}

/**
 * Cuenta regresiva de largada: pura función del reloj local, sin memoria propia — cualquier
 * cliente que lea el mismo [startAtMs] dibuja el mismo número al mismo tiempo (spec F4, sin
 * arbitraje de servidor).
 */
object RaceCountdown {
    private const val VISIBLE_BEFORE_MS = 5_000L
    private const val VISIBLE_AFTER_MS = 2_000L

    /** null = ocultar overlay. 0 = mostrar "¡YA!". 1..5 = segundos restantes para largar. */
    fun secondsToShow(startAtMs: Long, nowMs: Long): Int? {
        val remainingMs = startAtMs - nowMs
        if (remainingMs > VISIBLE_BEFORE_MS || remainingMs <= -VISIBLE_AFTER_MS) return null
        return if (remainingMs <= 0) 0 else ceil(remainingMs / 1_000.0).toInt()
    }

    /** true una vez pasada la ventana de visibilidad (más de [VISIBLE_AFTER_MS] tras la
     * largada) — señal para que el llamador deje de tiquear el reloj hasta la próxima carrera.
     * false tanto antes de entrar a la ventana como dentro de ella: el ticker debe seguir vivo
     * en ambos casos, solo se apaga cuando ya no hay nada más que mostrar. */
    fun isFinished(startAtMs: Long, nowMs: Long): Boolean = startAtMs - nowMs <= -VISIBLE_AFTER_MS
}

/**
 * Anuncio de llegada propia: reduce puro de una emisión de ranking a (nuevo estado, ¿anunciar
 * ahora?). Cruce real, no posición de largada — spec F4: un rider que ya está a <40 m del
 * destino cuando arman la carrera (rematch) no "llega", porque nunca cruzó nada.
 */
object RaceArrival {

    data class State(
        val raceKey: Long? = null,
        val wasArrived: Boolean = false,
        val announced: Boolean = false,
    )

    /**
     * [raceStartAtMs] null = sin carrera (resetea todo). Al ver un [raceStartAtMs] nuevo se
     * siembra `wasArrived = arrived` tal cual está en ese instante — así quien ya está en el
     * destino al armarse la carrera no genera un falso cruce false→true. El anuncio en sí
     * exige además haber pasado la largada ([nowMs] >= [raceStartAtMs]): un cruce durante el
     * countdown se registra en el estado pero no se anuncia hasta que la carrera arrancó, y no
     * puede "reaparecer" después porque [wasArrived] ya quedó en true.
     */
    fun step(state: State, raceStartAtMs: Long?, arrived: Boolean, nowMs: Long): Pair<State, Boolean> {
        if (raceStartAtMs == null) return State() to false
        val baseline = if (state.raceKey == raceStartAtMs) state else State(raceKey = raceStartAtMs, wasArrived = arrived)
        val crossedNow = arrived && !baseline.wasArrived
        val shouldAnnounce = crossedNow && nowMs >= raceStartAtMs && !baseline.announced
        val next = baseline.copy(wasArrived = arrived, announced = baseline.announced || shouldAnnounce)
        return next to shouldAnnounce
    }
}

/** Panel colapsable de posiciones en vivo de la sala respecto del destino compartido. Pasa a
 * modo carrera (posiciones prominentes + control de largada/detención) cuando [race] no es null. */
@Composable
fun Leaderboard(
    entries: List<RankingCalc.Entry>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    race: RoomClient.RaceState? = null,
    selfRiderName: String = "",
    onStartRace: () -> Unit = {},
    onStopRace: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(color = PanelBg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
            ) {
                Text(
                    "🏁 Posiciones (${entries.size})",
                    color = AccentYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Contraer posiciones" else "Expandir posiciones",
                    tint = AccentYellow,
                )
            }
            RaceControlRow(
                race = race,
                selfRiderName = selfRiderName,
                onStartRace = onStartRace,
                onStopRace = onStopRace,
            )
            if (expanded) {
                Spacer(Modifier.width(4.dp))
                entries.forEachIndexed { index, entry ->
                    LeaderboardRow(position = index + 1, entry = entry, raceMode = race != null)
                }
            }
        }
    }
}

/** Fila de control de carrera: largar (cualquiera), detener (solo quien largó) o estado pasivo. */
@Composable
private fun RaceControlRow(
    race: RoomClient.RaceState?,
    selfRiderName: String,
    onStartRace: () -> Unit,
    onStopRace: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        when {
            race == null -> TextButton(onClick = onStartRace) {
                Text("🏁 Largar carrera", color = AccentYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            race.startedBy == selfRiderName -> TextButton(onClick = onStopRace) {
                Text("Detener carrera", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            else -> Text(
                "Carrera en curso · largada por ${race.startedBy}",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LeaderboardRow(position: Int, entry: RankingCalc.Entry, raceMode: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            if (raceMode) positionBadge(position) else "$position.",
            color = if (entry.isSelf) AccentYellow else TextMuted,
            fontSize = if (raceMode) 16.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(if (raceMode) 28.dp else 20.dp),
        )
        Text(
            entry.name,
            color = if (entry.isSelf) AccentYellow else TextPrimary,
            fontSize = if (raceMode) 14.sp else 12.sp,
            fontWeight = if (entry.isSelf) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (entry.arrived) "Llegó ✓" else "${formatRemaining(entry.remainingM)} · ${formatEta(entry.etaMin)}",
            color = TextMuted,
            fontSize = 11.sp,
        )
    }
}

/** Medalla para el podio en modo carrera; el resto sigue con el número plano. */
private fun positionBadge(position: Int): String = when (position) {
    1 -> "🥇"
    2 -> "🥈"
    3 -> "🥉"
    else -> "$position."
}

/** Overlay grande y centrado de cuenta regresiva de largada — [secondsToShow] ya resuelto
 * por [RaceCountdown.secondsToShow] a partir del reloj local. */
@Composable
fun RaceCountdownOverlay(secondsToShow: Int, modifier: Modifier = Modifier) {
    Surface(color = Color(0xCC0A0A0F), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Text(
            if (secondsToShow > 0) "$secondsToShow" else "¡YA!",
            color = AccentYellow,
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
        )
    }
}

private fun formatRemaining(remainingM: Double): String =
    if (remainingM >= 1_000) "%.1f km".format(remainingM / 1_000.0) else "${remainingM.toInt()} m"

private fun formatEta(etaMin: Double?): String =
    etaMin?.let { "${it.toInt()} min" } ?: "—"
