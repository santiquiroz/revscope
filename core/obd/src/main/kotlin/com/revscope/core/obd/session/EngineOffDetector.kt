package com.revscope.core.obd.session

/**
 * Tracks recent vehicle movement so a link loss can be classified:
 * stationary + link dead usually means the ignition was turned off.
 */
class EngineOffDetector(private val clock: () -> Long = System::currentTimeMillis) {

    enum class LinkLossCause { ENGINE_OFF, LINK_FAULT }

    private var lastMovementTs: Long? = null

    fun onSpeed(kmh: Double) {
        if (kmh >= MOVING_THRESHOLD_KMH) lastMovementTs = clock()
    }

    fun movedRecently(): Boolean =
        lastMovementTs?.let { clock() - it <= RECENT_MOVEMENT_WINDOW_MS } ?: false

    fun reset() {
        lastMovementTs = null
    }

    companion object {
        const val MOVING_THRESHOLD_KMH = 3.0
        const val RECENT_MOVEMENT_WINDOW_MS = 30_000L
    }
}
