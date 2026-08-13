package com.revscope.core.navigation

/**
 * Traduce "cuántos pasos faltan" —lo que reporta el motor de progreso— a "cuál es la maniobra
 * que hay que anunciar".
 *
 * Está aparte y es puro porque es justo donde se desalinean las instrucciones, y un
 * desfase de uno significa cantar el giro de la cuadra siguiente. En OSRM la maniobra ocurre
 * al **principio** del paso, así que mientras uno circula por el paso `i` lo que se aproxima
 * es la maniobra del paso `i + 1`.
 */
object StepCursor {

    /** Índice del paso que se está recorriendo. */
    fun currentStepIndex(totalSteps: Int, remainingSteps: Int): Int =
        (totalSteps - remainingSteps).coerceIn(0, (totalSteps - 1).coerceAtLeast(0))

    /**
     * Índice de la maniobra que se aproxima. En el último paso ya no hay siguiente, y ese
     * último paso es el de llegada, así que se queda ahí.
     */
    fun nextManeuverIndex(totalSteps: Int, remainingSteps: Int): Int =
        (currentStepIndex(totalSteps, remainingSteps) + 1)
            .coerceAtMost((totalSteps - 1).coerceAtLeast(0))

    fun maneuverAhead(steps: List<RouteStep>, remainingSteps: Int): Maneuver? {
        if (steps.isEmpty()) return null
        return steps[nextManeuverIndex(steps.size, remainingSteps)].maneuver
    }
}
