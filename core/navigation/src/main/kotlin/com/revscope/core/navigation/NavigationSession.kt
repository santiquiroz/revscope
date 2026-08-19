package com.revscope.core.navigation

import timber.log.Timber
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.CourseOverGround
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavState
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.Navigator
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.Speed
import uniffi.ferrostar.TripState
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.createNavigator
import uniffi.ferrostar.createRouteFromOsrmRoute
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep
import java.time.Instant

/**
 * Una sesión de navegación viva.
 *
 * Reparte el trabajo según lo que quedó medido en `FerrostarOsrmContractTest`: Ferrostar hace
 * lo difícil de hacer bien —enganchar la posición a la ruta, avanzar de paso, detectar el
 * desvío y llevar el progreso— y las maniobras salen de [OsrmRouteParser], porque el OSRM
 * público no manda `bannerInstructions` y Ferrostar se queda sin nada que traducir.
 *
 * Los dos ven los mismos pasos en el mismo orden; ese cruce por índice está fijado por test.
 */
class NavigationSession private constructor(
    private val navigator: Navigator,
    private val steps: List<RouteStep>,
) : AutoCloseable {

    private var navState: NavState? = null

    fun start(fix: LocationFix): NavigationState =
        navigator.getInitialState(fix.toUserLocation()).let(::adopt)

    fun update(fix: LocationFix): NavigationState {
        val previous = navState ?: return start(fix)
        return navigator.updateUserLocation(fix.toUserLocation(), previous).let(::adopt)
    }

    override fun close() = navigator.close()

    private fun adopt(state: NavState): NavigationState {
        navState = state
        return describe(state.tripState)
    }

    private fun describe(trip: TripState): NavigationState = when (trip) {
        is TripState.Navigating -> {
            val maneuverIndex = StepCursor.nextManeuverIndex(steps.size, trip.remainingSteps.size)
            NavigationState(
                maneuver = StepCursor.maneuverAhead(steps, trip.remainingSteps.size),
                nextManeuver = steps.getOrNull(maneuverIndex + 1)?.maneuver,
                maneuverIndex = maneuverIndex,
                distanceToManeuverM = trip.progress.distanceToNextManeuver.toInt(),
                distanceRemainingM = trip.progress.distanceRemaining.toInt(),
                durationRemainingS = trip.progress.durationRemaining.toInt(),
                snapped = trip.snappedUserLocation.coordinates.toLatLon(),
                offRoute = trip.deviation is RouteDeviation.Deviation,
                arrived = false,
            )
        }
        is TripState.Complete -> NavigationState.IDLE.copy(
            maneuver = steps.lastOrNull()?.maneuver,
            maneuverIndex = steps.lastIndex,
            arrived = true,
        )
        else -> NavigationState.IDLE
    }

    companion object {

        /** Ancho del corredor fuera del cual se considera que uno se salió de la ruta. */
        private const val MAX_DEVIATION_M = 50.0

        /** Por debajo de esta exactitud del GPS no se juzga un desvío: sería ruido. */
        private const val MIN_ACCURACY_FOR_DEVIATION_M: UShort = 25u

        /** Ventana alrededor de la maniobra para dar el paso por cumplido. */
        private const val STEP_ENTRY_M: UShort = 20u
        private const val STEP_EXIT_M: UShort = 15u
        private const val STEP_MIN_ACCURACY_M: UShort = 15u

        /** Radio para dar el destino por alcanzado. */
        private const val ARRIVAL_RADIUS_M: UShort = 20u

        /**
         * [osrmRouteJson] es `routes[0]` de la respuesta, no la respuesta entera: quedó medido
         * que la vía de respuesta completa rechaza lo que devuelve el OSRM público.
         */
        fun create(
            osrmRouteJson: String,
            route: NavigationRoute,
            origin: LatLon,
            destination: LatLon,
            polylinePrecision: Int,
        ): NavigationSession? = runCatching {
            val ferrostarRoute = createRouteFromOsrmRoute(
                osrmRouteJson.toByteArray(),
                listOf(waypoint(origin), waypoint(destination)),
                polylinePrecision.toUInt(),
            )
            NavigationSession(createNavigator(ferrostarRoute, config(), false), route.steps)
        }.onFailure { Timber.w(it, "NavigationSession: no se pudo iniciar la navegación") }
            .getOrNull()

        private fun waypoint(at: LatLon) =
            Waypoint(GeographicCoordinate(at.lat, at.lon), WaypointKind.BREAK, null)

        private fun config() = NavigationControllerConfig(
            waypointAdvance = WaypointAdvanceMode.WaypointWithinRange(MAX_DEVIATION_M),
            stepAdvanceCondition = stepAdvance(),
            // El último paso no se puede dar por cumplido "saliendo" de él: no hay siguiente
            // por donde salir. Medido: con la misma condición que los demás pasos, la llegada
            // nunca se dispara y el estado se queda en navegando con distancia cero.
            arrivalStepAdvanceCondition = stepAdvanceDistanceToEndOfStep(
                ARRIVAL_RADIUS_M,
                STEP_MIN_ACCURACY_M,
            ),
            routeDeviationTracking = RouteDeviationTracking.StaticThreshold(
                minimumHorizontalAccuracy = MIN_ACCURACY_FOR_DEVIATION_M,
                maxAcceptableDeviation = MAX_DEVIATION_M,
            ),
            snappedLocationCourseFiltering = CourseFiltering.SNAP_TO_ROUTE,
        )

        private fun stepAdvance() = stepAdvanceDistanceEntryAndExit(
            STEP_ENTRY_M,
            STEP_EXIT_M,
            STEP_MIN_ACCURACY_M,
        )
    }
}

private fun LocationFix.toUserLocation() = UserLocation(
    coordinates = GeographicCoordinate(lat, lon),
    horizontalAccuracy = accuracyM,
    courseOverGround = bearingDeg?.let { CourseOverGround(it.toUShort(), null) },
    timestamp = Instant.ofEpochMilli(timestampMs),
    speed = speedMps?.let { Speed(it, null) },
)

private fun GeographicCoordinate.toLatLon() = LatLon(lat = lat, lon = lng)
