package com.revscope.core.navigation

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Una maniobra tal como la entrega OSRM: `type` y `modifier` del objeto `maneuver`, más el
 * nombre de la vía del paso. Ninguno está garantizado salvo `type`.
 */
data class Maneuver(
    val type: String,
    val modifier: String?,
    val streetName: String?,
)

/**
 * Arma la instrucción hablada en español.
 *
 * Existe porque el OSRM público **no** devuelve `voiceInstructions` ni `bannerInstructions`
 * — son extensiones de Mapbox, verificado contra el servicio real. Y aunque las devolviera,
 * vendrían en inglés: la voz de esta app sale por AlertsEngine, en español, al
 * intercomunicador del casco.
 *
 * Puro y determinístico: la frase depende solo de sus argumentos.
 */
object ManeuverSpeech {

    /** Debajo de esto ya no tiene sentido anunciar una distancia. */
    private const val IMMEDIATE_M = 30

    /** Los metros se redondean a este múltiplo: "en 237 metros" no es una instrucción útil. */
    private const val METERS_ROUNDING = 50

    fun spoken(maneuver: Maneuver, distanceM: Int): String {
        val action = action(maneuver)
        if (maneuver.type == "arrive") return action
        return "${distancePrefix(distanceM)}, $action"
    }

    private fun distancePrefix(distanceM: Int): String = when {
        distanceM < IMMEDIATE_M -> "Ahora"
        distanceM < 1_000 -> {
            val rounded = ((distanceM.toDouble() / METERS_ROUNDING).roundToInt() * METERS_ROUNDING)
                .coerceAtLeast(METERS_ROUNDING)
            "En $rounded metros"
        }
        else -> {
            val km = distanceM / 1_000.0
            // Coma decimal a propósito: lo lee un TTS en español.
            val text = if (km % 1.0 == 0.0) {
                km.toInt().toString()
            } else {
                String.format(Locale("es"), "%.1f", km)
            }
            val unit = if (text == "1") "kilómetro" else "kilómetros"
            "En $text $unit"
        }
    }

    private fun action(maneuver: Maneuver): String {
        val street = maneuver.streetName?.trim()?.takeIf { it.isNotEmpty() }
        val side = side(maneuver.modifier)

        return when (maneuver.type) {
            "arrive" -> "Llegó a su destino"
            "depart" -> withStreet("Comience", street, "por")
            "off ramp" -> withStreet(joinSide("tome la salida", side), street, "hacia")
            "on ramp" -> withStreet(joinSide("tome la rampa", side), street, "hacia")
            "merge" -> withStreet(joinSide("incorpórese", side), street, "a")
            "fork" -> withStreet(keepPhrase(side), street, "en")
            "end of road" -> withStreet("al final de la vía ${turnPhrase(side)}", street, "en")
            "roundabout", "rotary", "roundabout turn" -> withStreet("entre a la glorieta", street, "hacia")
            "exit roundabout", "exit rotary" -> withStreet("salga de la glorieta", street, "hacia")
            "turn" -> withStreet(turnPhrase(side), street, "en")
            "new name", "continue", "notification" -> withStreet("continúe", street, "por")
            // Un tipo que no conocemos es mejor anunciarlo como "continúe" que callarse.
            else -> withStreet("continúe", street, "por")
        }
    }

    private fun turnPhrase(side: String?): String = when (side) {
        null -> "continúe"
        "en U" -> "haga un giro en U"
        else -> "gire $side"
    }

    private fun keepPhrase(side: String?): String =
        if (side == null) "continúe" else "manténgase ${side.removePrefix("a la ").let { "a la $it" }}"

    private fun joinSide(verb: String, side: String?): String =
        if (side == null) verb else "$verb $side"

    private fun withStreet(action: String, street: String?, preposition: String): String =
        if (street == null) action else "$action $preposition $street"

    /** Traduce el `modifier` de OSRM. Devuelve null cuando no aporta dirección. */
    private fun side(modifier: String?): String? = when (modifier) {
        "right" -> "a la derecha"
        "left" -> "a la izquierda"
        "slight right" -> "levemente a la derecha"
        "slight left" -> "levemente a la izquierda"
        "sharp right" -> "cerrado a la derecha"
        "sharp left" -> "cerrado a la izquierda"
        "uturn" -> "en U"
        else -> null
    }
}
