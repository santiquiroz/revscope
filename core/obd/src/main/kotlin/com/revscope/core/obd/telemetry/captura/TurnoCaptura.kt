package com.revscope.core.obd.telemetry.captura

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
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
        const val CAPTURA_RAPIDA = "quick_capture"
        const val RAFAGA_VOLTAJE = "voltage_burst"

        fun ocupado(por: String?, resolver: StringResolver): String {
            val name = when (por) {
                CAPTURA_RAPIDA -> resolver.get(R.string.obd_capture_quick_name)
                RAFAGA_VOLTAJE -> resolver.get(R.string.obd_capture_voltage_name)
                else -> resolver.get(R.string.obd_capture_other)
            }
            return resolver.get(R.string.obd_capture_busy, name)
        }
    }
}
