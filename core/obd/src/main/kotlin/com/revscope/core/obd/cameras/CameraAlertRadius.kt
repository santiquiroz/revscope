package com.revscope.core.obd.cameras

/** Radio del aviso por voz de radares — configurable en Ajustes, con límites sanos. */
object CameraAlertRadius {
    const val DEFAULT_M = 250
    const val MIN_M = 100
    const val MAX_M = 1_000

    fun sanitize(value: Int?): Int = (value ?: DEFAULT_M).coerceIn(MIN_M, MAX_M)
}
