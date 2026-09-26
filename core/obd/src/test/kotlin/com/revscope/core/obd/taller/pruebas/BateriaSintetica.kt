package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.pruebas.AnalizadorBateria.Pasos
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

// Batería y carga armadas a mano: AT RV a [hz] por paso (contacto 10 s, arranque 8 s, mínimo 15 s y rpm
// altas 10 s). Cada serie es una función del tiempo del paso (s); null = el adaptador no respondió.
object BateriaSintetica {

    const val CONTACTO_MS = 10_000L
    const val ARRANQUE_MS = 8_000L
    const val MINIMO_MS = 15_000L
    const val RPM_ALTAS_MS = 10_000L

    data class Perfil(
        val contacto: (Double) -> Double? = { 12.6 },
        val arranque: (Double) -> Double? = arranque(),
        val minimo: ((Double) -> Double?)? = { 13.8 },
        val rpmAltas: ((Double) -> Double?)? = oscilar(14.1, 14.3),
        val arranqueMs: Long = ARRANQUE_MS,
        val hz: Int = 20,
        val contexto: ContextoAnalisis = ContextoAnalisis(),
    )

    fun datos(p: Perfil = Perfil()): DatosPrueba {
        val segmentos = mutableListOf<SegmentoPaso>()
        val muestras = mutableListOf<MuestraCaptura>()
        var t = 0L
        fun paso(clave: String, ms: Long, descartarMs: Long, serie: (Double) -> Double?) {
            segmentos += SegmentoPaso(clave, t, t + ms, descartarMs)
            muestras += muestras(t, ms, p.hz, serie)
            t += ms
        }
        paso(Pasos.CONTACTO, CONTACTO_MS, 1_000, p.contacto)
        paso(Pasos.ARRANQUE, p.arranqueMs, 0, p.arranque)
        p.minimo?.let { paso(Pasos.MINIMO, MINIMO_MS, 1_000, it) }
        p.rpmAltas?.let { paso(Pasos.RPM_ALTAS, RPM_ALTAS_MS, 1_000, it) }
        return DatosPrueba(TipoPrueba.BATERIA_CARGA, muestras.mapIndexed { i, m -> m.copy(seq = i.toLong()) }, segmentos, contexto = p.contexto)
    }

    // Reposo, caída a [valle] durante 0,3 s al empezar a girar (a 1,0 s), 11 V mientras gira y [carga] cuando prende (a 2,5 s).
    fun arranque(reposo: Double = 12.6, valle: Double = 10.4, carga: Double = 13.6): (Double) -> Double? = { s ->
        when {
            s < 1.0 -> reposo
            s < 1.3 -> valle
            s < 2.5 -> 11.0
            else -> carga
        }
    }

    // El adaptador se apaga al caer el voltaje (a 1,1 s) y no vuelve a responder.
    fun arranqueQueApagaElAdaptador(reposo: Double = 12.4): (Double) -> Double? = { s ->
        when {
            s < 1.0 -> reposo
            s < 1.1 -> 9.8
            else -> null
        }
    }

    // El adaptador deja de responder 1,5 s (de 1,1 a 2,6 s) y vuelve con el motor ya en marcha.
    fun arranqueConHueco(): (Double) -> Double? = { s ->
        when {
            s < 1.0 -> 12.4
            s < 1.1 -> 10.2
            s < 2.6 -> null
            else -> 13.6
        }
    }

    fun oscilar(desde: Double, hasta: Double): (Double) -> Double = { s -> if ((s * 2).toInt() % 2 == 0) desde else hasta }

    private fun muestras(inicioMs: Long, ms: Long, hz: Int, serie: (Double) -> Double?): List<MuestraCaptura> {
        val n = (ms * hz / 1_000).toInt()
        return (0 until n).mapNotNull { i ->
            val s = i.toDouble() / hz
            val tMs = inicioMs + i * 1_000L / hz
            serie(s)?.let { MuestraCaptura(0, tMs * 1_000, AnalizadorBateria.PID_VOLTAJE, it, tMs, 40) }
        }
    }
}
