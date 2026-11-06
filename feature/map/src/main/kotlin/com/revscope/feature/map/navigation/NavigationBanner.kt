package com.revscope.feature.map.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.navigation.Maneuver
import com.revscope.core.navigation.NavigationState
import com.revscope.feature.map.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * La instrucción que se está siguiendo, arriba del mapa.
 *
 * Se lee de un vistazo, con guantes y en movimiento: una flecha grande, la distancia en el
 * tamaño más grande de la pantalla, y la calle debajo. Todo lo demás va al pie.
 */
@Composable
fun NavigationBanner(state: NavigationState, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = PANEL, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Icon(
                imageVector = maneuverIcon(state),
                contentDescription = null,
                tint = ACCENT,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    headline(state),
                    color = ACCENT,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                subtitle(state)?.let {
                    Text(it, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                chained(state)?.let {
                    Text(it, color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.map_nav_end), tint = MUTED)
            }
        }
    }
}

/** Lo que falta del viaje, al pie: velocidad actual (si se conoce), distancia, tiempo y hora
 * de llegada — velocidad como primera celda para no necesitar un badge suelto aparte durante
 * la navegación (ver SpeedOverlay en LiveMapScreen, que se oculta mientras esta barra manda). */
@Composable
fun NavigationProgressBar(state: NavigationState, speedKmh: Int? = null, modifier: Modifier = Modifier) {
    Surface(color = PANEL, shape = RoundedCornerShape(10.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            // weight(fill=false): con 4 celdas en 320dp el Row sin pesos mide con ancho ilimitado
            // y el Surface CLIPEA la última celda en silencio; con pesos, reparten y nada se corta.
            speedKmh?.let { ProgressField("$it km/h", stringResource(R.string.map_nav_field_speed), Modifier.weight(1f, fill = false)) } // i18n-ignore
            ProgressField(formatDistance(state.distanceRemainingM), stringResource(R.string.map_nav_field_remaining), Modifier.weight(1f, fill = false))
            ProgressField(formatDuration(state.durationRemainingS), stringResource(R.string.map_nav_field_en_route), Modifier.weight(1f, fill = false))
            ProgressField(arrivalClock(state.durationRemainingS), stringResource(R.string.map_nav_field_arrival), Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun ProgressField(value: String, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value, color = ACCENT, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MUTED, fontSize = 11.sp)
    }
}

@Composable
private fun headline(state: NavigationState): String = when {
    state.arrived -> stringResource(R.string.map_nav_arrived)
    state.offRoute -> stringResource(R.string.map_nav_recalculating)
    else -> formatDistance(state.distanceToManeuverM)
}

@Composable
private fun subtitle(state: NavigationState): String? = when {
    state.arrived -> stringResource(R.string.map_nav_arrived_destination)
    state.offRoute -> stringResource(R.string.map_nav_off_route)
    else -> state.maneuver?.let { maneuverLabel(it) }
}

/** "Luego: ..." solo cuando las maniobras vienen pegadas; si no, es ruido. */
@Composable
private fun chained(state: NavigationState): String? {
    if (state.arrived || state.offRoute) return null
    if (state.distanceToManeuverM >= CHAIN_THRESHOLD_M) return null
    val next = state.nextManeuver ?: return null
    return stringResource(R.string.map_nav_then, maneuverLabel(next))
}

@Composable
private fun maneuverLabel(maneuver: Maneuver): String {
    val street = maneuver.streetName?.trim()?.takeIf { it.isNotEmpty() }
    val action = stringResource(maneuverActionRes(maneuver))
    return if (street == null) action else "$action · $street"
}

@StringRes
private fun maneuverActionRes(maneuver: Maneuver): Int = when (maneuver.type) {
    "arrive" -> R.string.map_maneuver_arrive
    "roundabout", "rotary", "roundabout turn" -> R.string.map_maneuver_roundabout // i18n-ignore
    "exit roundabout", "exit rotary" -> R.string.map_maneuver_exit_roundabout // i18n-ignore
    "off ramp" -> R.string.map_maneuver_off_ramp // i18n-ignore
    "on ramp" -> R.string.map_maneuver_on_ramp // i18n-ignore
    "merge" -> R.string.map_maneuver_merge
    "fork" -> R.string.map_maneuver_fork
    "end of road" -> R.string.map_maneuver_end_of_road // i18n-ignore
    "turn" -> sideLabel(maneuver.modifier) ?: R.string.map_maneuver_turn
    else -> R.string.map_maneuver_continue
}

@StringRes
private fun sideLabel(modifier: String?): Int? = when (modifier) {
    "right" -> R.string.map_maneuver_right
    "left" -> R.string.map_maneuver_left
    "slight right" -> R.string.map_maneuver_slight_right // i18n-ignore
    "slight left" -> R.string.map_maneuver_slight_left // i18n-ignore
    "sharp right" -> R.string.map_maneuver_sharp_right // i18n-ignore
    "sharp left" -> R.string.map_maneuver_sharp_left // i18n-ignore
    "uturn" -> R.string.map_maneuver_uturn
    else -> null
}

private fun maneuverIcon(state: NavigationState): ImageVector {
    if (state.arrived) return Icons.Default.Flag
    if (state.offRoute) return Icons.Default.Navigation
    val maneuver = state.maneuver ?: return Icons.Default.Straight
    turnIcon(maneuver.modifier)?.let { return it }
    return when (maneuver.type) {
        "arrive" -> Icons.Default.Flag
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> // i18n-ignore
            Icons.Default.RoundaboutRight
        "merge" -> Icons.Default.Merge
        else -> Icons.Default.Straight
    }
}

private fun turnIcon(modifier: String?): ImageVector? = when (modifier) {
    "right" -> Icons.Default.TurnRight
    "left" -> Icons.Default.TurnLeft
    "slight right" -> Icons.Default.TurnSlightRight // i18n-ignore
    "slight left" -> Icons.Default.TurnSlightLeft // i18n-ignore
    "sharp right" -> Icons.Default.TurnSharpRight // i18n-ignore
    "sharp left" -> Icons.Default.TurnSharpLeft // i18n-ignore
    "uturn" -> Icons.Default.UTurnLeft
    else -> null
}

/** Mismo redondeo que la voz: si se oye "en 250 metros", en pantalla no puede decir 237. */
@Composable
private fun formatDistance(distanceM: Int): String = when {
    distanceM < IMMEDIATE_M -> stringResource(R.string.map_nav_now)
    distanceM < 1_000 -> "${(distanceM / METERS_ROUNDING).coerceAtLeast(1) * METERS_ROUNDING} m"
    else -> LocaleNumbers.decimal(distanceM / 1_000.0, 1, LocalConfiguration.current.locales[0]) + " km" // i18n-ignore
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min" // i18n-ignore
}

private fun arrivalClock(secondsRemaining: Int): String =
    LocalTime.now().plusSeconds(secondsRemaining.toLong()).format(CLOCK)

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val IMMEDIATE_M = 30
private const val METERS_ROUNDING = 50
private const val CHAIN_THRESHOLD_M = 150

private val PANEL = Color(0xF2121218)
private val ACCENT = Color(0xFFE8FF00)
private val MUTED = Color(0xFF6B7089)
