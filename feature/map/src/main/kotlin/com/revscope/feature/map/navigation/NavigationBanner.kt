package com.revscope.feature.map.navigation

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.navigation.Maneuver
import com.revscope.core.navigation.NavigationState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Close, contentDescription = "Terminar navegación", tint = MUTED)
            }
        }
    }
}

/** Lo que falta del viaje, al pie: distancia, tiempo y hora de llegada. */
@Composable
fun NavigationProgressBar(state: NavigationState, modifier: Modifier = Modifier) {
    Surface(color = PANEL, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            ProgressField(formatDistance(state.distanceRemainingM), "restante")
            ProgressField(formatDuration(state.durationRemainingS), "en camino")
            ProgressField(arrivalClock(state.durationRemainingS), "llegada")
        }
    }
}

@Composable
private fun ProgressField(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ACCENT, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MUTED, fontSize = 11.sp)
    }
}

private fun headline(state: NavigationState): String = when {
    state.arrived -> "Llegó"
    state.offRoute -> "Recalculando…"
    else -> formatDistance(state.distanceToManeuverM)
}

private fun subtitle(state: NavigationState): String? = when {
    state.arrived -> "Llegó a su destino"
    state.offRoute -> "Se salió de la ruta"
    else -> state.maneuver?.let(::maneuverLabel)
}

private fun maneuverLabel(maneuver: Maneuver): String {
    val street = maneuver.streetName?.trim()?.takeIf { it.isNotEmpty() }
    val action = when (maneuver.type) {
        "arrive" -> "Llegada"
        "roundabout", "rotary", "roundabout turn" -> "Glorieta"
        "exit roundabout", "exit rotary" -> "Salir de la glorieta"
        "off ramp" -> "Tomar la salida"
        "on ramp" -> "Tomar la rampa"
        "merge" -> "Incorporarse"
        "fork" -> "Bifurcación"
        "end of road" -> "Fin de la vía"
        "turn" -> sideLabel(maneuver.modifier) ?: "Girar"
        else -> "Seguir"
    }
    return if (street == null) action else "$action · $street"
}

private fun sideLabel(modifier: String?): String? = when (modifier) {
    "right" -> "Derecha"
    "left" -> "Izquierda"
    "slight right" -> "Leve derecha"
    "slight left" -> "Leve izquierda"
    "sharp right" -> "Cerrado derecha"
    "sharp left" -> "Cerrado izquierda"
    "uturn" -> "Giro en U"
    else -> null
}

private fun maneuverIcon(state: NavigationState): ImageVector {
    if (state.arrived) return Icons.Default.Flag
    if (state.offRoute) return Icons.Default.Navigation
    val maneuver = state.maneuver ?: return Icons.Default.Straight
    turnIcon(maneuver.modifier)?.let { return it }
    return when (maneuver.type) {
        "arrive" -> Icons.Default.Flag
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" ->
            Icons.Default.RoundaboutRight
        "merge" -> Icons.Default.Merge
        else -> Icons.Default.Straight
    }
}

private fun turnIcon(modifier: String?): ImageVector? = when (modifier) {
    "right" -> Icons.Default.TurnRight
    "left" -> Icons.Default.TurnLeft
    "slight right" -> Icons.Default.TurnSlightRight
    "slight left" -> Icons.Default.TurnSlightLeft
    "sharp right" -> Icons.Default.TurnSharpRight
    "sharp left" -> Icons.Default.TurnSharpLeft
    "uturn" -> Icons.Default.UTurnLeft
    else -> null
}

/** Mismo redondeo que la voz: si se oye "en 250 metros", en pantalla no puede decir 237. */
private fun formatDistance(distanceM: Int): String = when {
    distanceM < IMMEDIATE_M -> "Ahora"
    distanceM < 1_000 -> "${(distanceM / METERS_ROUNDING).coerceAtLeast(1) * METERS_ROUNDING} m"
    else -> String.format(Locale("es"), "%.1f km", distanceM / 1_000.0)
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

private fun arrivalClock(secondsRemaining: Int): String =
    LocalTime.now().plusSeconds(secondsRemaining.toLong()).format(CLOCK)

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val IMMEDIATE_M = 30
private const val METERS_ROUNDING = 50

private val PANEL = Color(0xF2121218)
private val ACCENT = Color(0xFFE8FF00)
private val MUTED = Color(0xFF6B7089)
