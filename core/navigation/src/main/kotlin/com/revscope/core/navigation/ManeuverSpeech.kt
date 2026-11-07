package com.revscope.core.navigation

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.units.DistanceUnit
import com.revscope.core.common.units.UnitFormatter
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
 * Arma la instrucción hablada en el idioma de [StringResolver].
 *
 * Existe porque el OSRM público **no** devuelve `voiceInstructions` ni `bannerInstructions`
 * — son extensiones de Mapbox, verificado contra el servicio real. Y aunque las devolviera,
 * vendrían solo en inglés: la voz de esta app sale por AlertsEngine, en el idioma de la app,
 * al intercomunicador del casco.
 *
 * Puro y determinístico: la frase depende solo de sus argumentos.
 */
object ManeuverSpeech {

    /** Debajo de esto ya no tiene sentido anunciar una distancia. */
    private const val IMMEDIATE_M = 30

    /** Metros y pies se redondean a este múltiplo: 'en 237 metros' no es una instrucción útil. */
    private const val SHORT_ROUNDING = 50

    private const val TENTHS = 10.0

    fun spoken(maneuver: Maneuver, distanceM: Int, resolver: StringResolver, units: UnitFormatter): String {
        val action = action(maneuver, resolver)
        if (maneuver.type == "arrive") return action
        return resolver.get(R.string.nav_spoken_with_distance, distancePrefix(distanceM, resolver, units), action)
    }

    private fun distancePrefix(distanceM: Int, resolver: StringResolver, units: UnitFormatter): String {
        if (distanceM < IMMEDIATE_M) return resolver.get(R.string.nav_distance_now)
        val measure = units.shortDistance(distanceM.toDouble())
        return when (measure.unit) {
            DistanceUnit.METERS -> shortPhrase(R.plurals.nav_spoken_meters, measure.value, resolver)
            DistanceUnit.FEET -> shortPhrase(R.plurals.nav_spoken_feet, measure.value, resolver)
            DistanceUnit.KILOMETERS ->
                longPhrase(R.plurals.nav_spoken_kilometers, R.string.nav_spoken_kilometers_decimal, measure.value, resolver)
            // Una milla nunca cae en metros enteros: sin redondear, 1609 m se diría "1.0 miles".
            DistanceUnit.MILES ->
                longPhrase(R.plurals.nav_spoken_miles, R.string.nav_spoken_miles_decimal, toTenth(measure.value), resolver)
        }
    }

    private fun shortPhrase(plural: Int, value: Double, resolver: StringResolver): String {
        val rounded = ((value / SHORT_ROUNDING).roundToInt() * SHORT_ROUNDING).coerceAtLeast(SHORT_ROUNDING)
        return resolver.plural(plural, rounded, rounded)
    }

    private fun toTenth(value: Double): Double = (value * TENTHS).roundToInt() / TENTHS

    private fun longPhrase(plural: Int, decimal: Int, value: Double, resolver: StringResolver): String {
        if (value % 1.0 == 0.0) return resolver.plural(plural, value.toInt(), value.toInt())
        // Separador decimal del idioma a propósito: en español lo lee un TTS que espera coma.
        val text = LocaleNumbers.decimal(value, 1, resolver.locale)
        return resolver.get(decimal, text)
    }

    private fun action(maneuver: Maneuver, resolver: StringResolver): String {
        val street = maneuver.streetName?.trim()?.takeIf { it.isNotEmpty() }
        val side = Side.of(maneuver.modifier)
        val r = resolver

        return when (maneuver.type) {
            "arrive" -> r.get(R.string.nav_arrived)
            "depart" -> withStreet(r.get(R.string.nav_depart), street, R.string.nav_street_along, r)
            "off ramp" -> withStreet( // i18n-ignore: tipo de OSRM
                joinSide(R.string.nav_take_exit, R.string.nav_take_exit_side, side, r),
                street, R.string.nav_street_toward, r,
            )
            "on ramp" -> withStreet( // i18n-ignore: tipo de OSRM
                joinSide(R.string.nav_take_ramp, R.string.nav_take_ramp_side, side, r),
                street, R.string.nav_street_toward, r,
            )
            "merge" -> withStreet(
                joinSide(R.string.nav_merge, R.string.nav_merge_side, side, r),
                street, R.string.nav_street_merge, r,
            )
            "fork" -> withStreet(keepPhrase(side, r), street, R.string.nav_street_at, r)
            "end of road" -> withStreet( // i18n-ignore: tipo de OSRM
                r.get(R.string.nav_end_of_road, turnPhrase(side, r)),
                street, R.string.nav_street_at, r,
            )
            "roundabout", "rotary", "roundabout turn" -> // i18n-ignore: tipos de OSRM
                withStreet(r.get(R.string.nav_enter_roundabout), street, R.string.nav_street_toward, r)
            "exit roundabout", "exit rotary" -> // i18n-ignore: tipos de OSRM
                withStreet(r.get(R.string.nav_exit_roundabout), street, R.string.nav_street_toward, r)
            "turn" -> withStreet(turnPhrase(side, r), street, R.string.nav_street_at, r)
            "new name", "continue", "notification" -> // i18n-ignore: tipos de OSRM
                withStreet(r.get(R.string.nav_continue), street, R.string.nav_street_along, r)
            // Un tipo que no conocemos es mejor anunciarlo como un seguir derecho que callarse.
            else -> withStreet(r.get(R.string.nav_continue), street, R.string.nav_street_along, r)
        }
    }

    private fun turnPhrase(side: Side?, resolver: StringResolver): String =
        resolver.get(side?.turn ?: R.string.nav_continue)

    private fun keepPhrase(side: Side?, resolver: StringResolver): String =
        resolver.get(side?.keep ?: R.string.nav_continue)

    private fun joinSide(verb: Int, verbWithSide: Int, side: Side?, resolver: StringResolver): String =
        if (side == null) resolver.get(verb) else resolver.get(verbWithSide, resolver.get(side.side))

    private fun withStreet(action: String, street: String?, template: Int, resolver: StringResolver): String =
        if (street == null) action else resolver.get(template, action, street)

    /**
     * El `modifier` de OSRM en sus tres usos: lado de una salida, giro y bifurcación. Van como
     * frases enteras porque el orden de las palabras cambia de un idioma a otro. Null cuando
     * no aporta dirección.
     */
    private enum class Side(val side: Int, val turn: Int, val keep: Int) {
        RIGHT(R.string.nav_side_right, R.string.nav_turn_right, R.string.nav_keep_right),
        LEFT(R.string.nav_side_left, R.string.nav_turn_left, R.string.nav_keep_left),
        SLIGHT_RIGHT(R.string.nav_side_slight_right, R.string.nav_turn_slight_right, R.string.nav_keep_slight_right),
        SLIGHT_LEFT(R.string.nav_side_slight_left, R.string.nav_turn_slight_left, R.string.nav_keep_slight_left),
        SHARP_RIGHT(R.string.nav_side_sharp_right, R.string.nav_turn_sharp_right, R.string.nav_keep_sharp_right),
        SHARP_LEFT(R.string.nav_side_sharp_left, R.string.nav_turn_sharp_left, R.string.nav_keep_sharp_left),
        UTURN(R.string.nav_side_uturn, R.string.nav_turn_uturn, R.string.nav_keep_uturn);

        companion object {
            fun of(modifier: String?): Side? = when (modifier) {
                "right" -> RIGHT
                "left" -> LEFT
                "slight right" -> SLIGHT_RIGHT // i18n-ignore: modifier de OSRM
                "slight left" -> SLIGHT_LEFT // i18n-ignore: modifier de OSRM
                "sharp right" -> SHARP_RIGHT // i18n-ignore: modifier de OSRM
                "sharp left" -> SHARP_LEFT // i18n-ignore: modifier de OSRM
                "uturn" -> UTURN
                else -> null
            }
        }
    }
}
