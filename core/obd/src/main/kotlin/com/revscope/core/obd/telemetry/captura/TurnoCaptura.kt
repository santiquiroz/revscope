package com.revscope.core.obd.telemetry.captura

import java.util.concurrent.atomic.AtomicReference

// Una captura a la vez sobre el adaptador: la rápida de PIDs o la ráfaga de AT RV de la prueba de batería.
// Las dos pausan el sondeo normal y se adueñan del canal; si convivieran, cada una reanudaría el sondeo de la otra.
class TurnoCaptura {

    private val dueno = AtomicReference<String?>(null)

    val ocupadoPor: String? get() = dueno.get()

    fun tomar(quien: String): Boolean = dueno.compareAndSet(null, quien)

    fun soltar(quien: String) {
        dueno.compareAndSet(quien, null)
    }

    companion object {
        const val CAPTURA_RAPIDA = "captura rápida"
        const val RAFAGA_VOLTAJE = "ráfaga de voltaje de la prueba de batería"

        fun ocupado(por: String?): String = "El adaptador está ocupado con la ${por ?: "otra captura"}: detenla primero"
    }
}
