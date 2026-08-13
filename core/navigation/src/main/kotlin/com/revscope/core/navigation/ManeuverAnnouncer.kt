package com.revscope.core.navigation

/**
 * Decide **cuándo** hablar. [ManeuverSpeech] decide qué decir.
 *
 * Va separado porque el problema de una navegación por voz no es armar la frase, es no
 * repetirla. Yendo en moto con el celular en el bolsillo, un aviso de más cada segundo es peor
 * que ninguno.
 *
 * Guarda estado, pero es determinístico: la misma secuencia de estados produce la misma
 * secuencia de frases.
 */
class ManeuverAnnouncer {

    private var currentManeuverIndex = -1
    private var closestTierAnnounced = Int.MAX_VALUE
    private var startedTalking = false
    private var announcedOffRoute = false
    private var announcedArrival = false

    /** La frase que toca decir ahora, o null si no hay nada nuevo que anunciar. */
    fun next(state: NavigationState): String? {
        if (state.arrived) return arrivalAnnouncement(state)
        announcedArrival = false
        if (state.offRoute) return offRouteAnnouncement()
        announcedOffRoute = false
        return maneuverAnnouncement(state)
    }

    fun reset() {
        currentManeuverIndex = -1
        closestTierAnnounced = Int.MAX_VALUE
        startedTalking = false
        announcedOffRoute = false
        announcedArrival = false
    }

    private fun arrivalAnnouncement(state: NavigationState): String? {
        if (announcedArrival) return null
        announcedArrival = true
        return ManeuverSpeech.spoken(state.maneuver ?: ARRIVAL, 0)
    }

    private fun offRouteAnnouncement(): String? {
        if (announcedOffRoute) return null
        announcedOffRoute = true
        return OFF_ROUTE_PHRASE
    }

    private fun maneuverAnnouncement(state: NavigationState): String? {
        val maneuver = state.maneuver ?: return null
        rewindOnNewManeuver(state.maneuverIndex)
        val tier = tierFor(state.distanceToManeuverM) ?: return firstWordOfTheTrip(state, maneuver)
        if (tier >= closestTierAnnounced) return null
        closestTierAnnounced = tier
        startedTalking = true
        return ManeuverSpeech.spoken(maneuver, state.distanceToManeuverM)
    }

    private fun rewindOnNewManeuver(index: Int) {
        if (index == currentManeuverIndex) return
        currentManeuverIndex = index
        closestTierAnnounced = Int.MAX_VALUE
    }

    /**
     * Al arrancar, la primera maniobra puede estar más lejos que cualquier umbral. Quedarse
     * callado hasta los 400 m dejaría al conductor sin saber para dónde arrancar.
     */
    private fun firstWordOfTheTrip(state: NavigationState, maneuver: Maneuver): String? {
        if (startedTalking) return null
        startedTalking = true
        return ManeuverSpeech.spoken(maneuver, state.distanceToManeuverM)
    }

    /** El umbral más cercano que la distancia ya cruzó; los más lejanos quedan saltados. */
    private fun tierFor(distanceM: Int): Int? =
        TIERS.filter { distanceM <= it }.minOrNull()

    private companion object {
        /** Avisar, preparar, y ejecutar. Tres son suficientes para no volverse cansón. */
        val TIERS = listOf(400, 150, 30)

        const val OFF_ROUTE_PHRASE = "Se salió de la ruta, recalculando"

        val ARRIVAL = Maneuver("arrive", null, null)
    }
}
