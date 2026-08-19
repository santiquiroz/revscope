package com.revscope.feature.map.navigation

/**
 * Cámara durante la navegación, estilo Google Maps: inclinada, rumbo arriba, y con el zoom
 * gobernado por dos señales — velocidad (rápido ve más lejos) y cercanía a la maniobra
 * (el giro se mira de cerca). Función pura: la pantalla solo aplica el número.
 */
object NavCamera {

    const val PITCH = 50.0

    private const val ZOOM_NEAR = 17.5
    private const val ZOOM_FAR = 15.5
    private const val SPEED_FOR_FAR_KMH = 90.0
    private const val APPROACH_START_M = 300.0

    fun zoom(speedKmh: Int?, distToManeuverM: Int): Double {
        val speed = (speedKmh ?: 0).coerceAtLeast(0).toDouble()
        val bySpeed = ZOOM_NEAR - (ZOOM_NEAR - ZOOM_FAR) * (speed / SPEED_FOR_FAR_KMH).coerceAtMost(1.0)
        if (distToManeuverM >= APPROACH_START_M) return bySpeed
        // Dentro de la ventana de aproximación, interpola de bySpeed hacia ZOOM_NEAR.
        val t = 1.0 - (distToManeuverM / APPROACH_START_M)
        return bySpeed + (ZOOM_NEAR - bySpeed) * t
    }
}
