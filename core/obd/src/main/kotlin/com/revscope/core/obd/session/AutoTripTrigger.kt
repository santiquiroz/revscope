package com.revscope.core.obd.session

/**
 * Auto-viaje: con el enlace vivo y sin grabar, una velocidad sostenida por encima del umbral
 * dispara un viaje nuevo. Los picos cortos (un 0D ruidoso, un empujón en el parqueadero) no.
 */
class AutoTripTrigger(
    private val umbralKmh: Double = UMBRAL_KMH,
    private val sostenidoMs: Long = SOSTENIDO_MS,
) {

    private var sobreUmbralDesdeMs: Long? = null

    /** true una sola vez por racha sostenida; luego exige volver a bajar del umbral. */
    fun onVelocidad(kmh: Double, ahoraMs: Long): Boolean {
        if (kmh < umbralKmh) {
            sobreUmbralDesdeMs = null
            return false
        }
        val desde = sobreUmbralDesdeMs ?: ahoraMs.also { sobreUmbralDesdeMs = it }
        if (desde == DISPARADO || ahoraMs - desde < sostenidoMs) return false
        sobreUmbralDesdeMs = DISPARADO
        return true
    }

    fun reset() {
        sobreUmbralDesdeMs = null
    }

    /** Tras finalizar un viaje en movimiento: no se rearma hasta bajar del umbral. */
    fun exigirParada() {
        sobreUmbralDesdeMs = DISPARADO
    }

    companion object {
        const val UMBRAL_KMH = 10.0
        const val SOSTENIDO_MS = 5_000L
        private const val DISPARADO = Long.MIN_VALUE
    }
}
