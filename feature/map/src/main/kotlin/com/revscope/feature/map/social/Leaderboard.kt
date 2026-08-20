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
import kotlin.math.max

private const val MIN_ETA_SPEED_KMH = 5.0
private const val DEFAULT_ARRIVAL_RADIUS_M = 40.0
private const val METERS_PER_KM = 1_000.0
private const val MINUTES_PER_HOUR = 60.0

private val PanelBg = Color(0xE6121218)
private val AccentYellow = Color(0xFFE8FF00)
private val TextPrimary = Color(0xFFF0F0F8)
private val TextMuted = Color(0xFF6B7089)

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

/** Panel colapsable de posiciones en vivo de la sala respecto del destino compartido. */
@Composable
fun Leaderboard(
    entries: List<RankingCalc.Entry>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
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
            if (expanded) {
                Spacer(Modifier.width(4.dp))
                entries.forEachIndexed { index, entry -> LeaderboardRow(position = index + 1, entry = entry) }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(position: Int, entry: RankingCalc.Entry) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            "$position.",
            color = if (entry.isSelf) AccentYellow else TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
        )
        Text(
            entry.name,
            color = if (entry.isSelf) AccentYellow else TextPrimary,
            fontSize = 12.sp,
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

private fun formatRemaining(remainingM: Double): String =
    if (remainingM >= 1_000) "%.1f km".format(remainingM / 1_000.0) else "${remainingM.toInt()} m"

private fun formatEta(etaMin: Double?): String =
    etaMin?.let { "${it.toInt()} min" } ?: "—"
