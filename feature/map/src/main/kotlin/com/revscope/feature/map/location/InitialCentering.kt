package com.revscope.feature.map.location

import com.revscope.core.obd.service.LiveRouteHolder.RoutePoint

data class CenterAction(val lat: Double, val lon: Double, val zoom: Double)

/**
 * Decide los centrados automáticos al abrir el mapa. Máquina de estados pura:
 * lastKnown centra una vez (lejos), el primer fix vivo re-centra una vez (cerca),
 * y un pan del usuario cancela todo — la cámara nunca le pelea al dedo.
 */
class InitialCentering {

    private var doneLastKnown = false
    private var doneLiveFix = false
    private var cancelled = false

    fun onLastKnown(p: RoutePoint?): CenterAction? {
        if (cancelled || doneLastKnown || doneLiveFix || p == null) return null
        doneLastKnown = true
        return CenterAction(p.lat, p.lon, IDLE_ZOOM)
    }

    fun onLiveFix(p: RoutePoint?): CenterAction? {
        if (cancelled || doneLiveFix || p == null) return null
        doneLiveFix = true
        doneLastKnown = true
        return CenterAction(p.lat, p.lon, INITIAL_ZOOM)
    }

    fun onUserPan() {
        cancelled = true
    }

    companion object {
        const val IDLE_ZOOM = 13.0
        const val INITIAL_ZOOM = 16.0
    }
}
