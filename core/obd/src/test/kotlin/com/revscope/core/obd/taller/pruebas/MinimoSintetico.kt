package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo.Pasos
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import kotlin.math.PI
import kotlin.math.sin

// Mínimo y retorno armados a mano: RPM, TPS (%) y ECT a 10 Hz. Cada serie es una función del tiempo del paso (s).
object MinimoSintetico {

    const val HZ = 10
    const val MINIMO_MS = 45_000L
    const val RETORNO_MS = 8_000L
    private const val PAUSA_MS = 500L

    fun datos(
        minimo: (Double) -> Double,
        retornos: List<(Double) -> Double>,
        minimoMs: Long = MINIMO_MS,
        tpsPct: Double? = 12.2,
        ectC: Double? = 85.0,
        descartarMinimoMs: Long = 1_000,
    ): DatosPrueba {
        val segmentos = mutableListOf<SegmentoPaso>()
        val muestras = mutableListOf<MuestraCaptura>()
        var t = 0L
        segmentos += SegmentoPaso(Pasos.MINIMO, t, t + minimoMs, descartarMinimoMs)
        muestras += paso(t, minimoMs, minimo, tpsPct, ectC)
        t += minimoMs + PAUSA_MS
        retornos.forEachIndexed { i, rpm ->
            segmentos += SegmentoPaso(Pasos.RETORNOS[i], t, t + RETORNO_MS, 0)
            muestras += paso(t, RETORNO_MS, rpm, tpsPct, ectC)
            t += RETORNO_MS + PAUSA_MS
        }
        return DatosPrueba(TipoPrueba.MINIMO_RETORNO, muestras.mapIndexed { i, m -> m.copy(seq = i.toLong()) }, segmentos)
    }

    fun estable(base: Double = 1_400.0, amplitud: Double = 15.0): (Double) -> Double =
        { s -> base + amplitud * sin(2 * PI * s / 3.0) }

    fun oscilante(base: Double = 1_400.0, amplitud: Double = 150.0, periodoS: Double = 4.0): (Double) -> Double =
        { s -> base + amplitud * sin(2 * PI * s / periodoS) }

    // Caso Benelli: de 2 010 a 1 454 rpm en 9 s, en línea recta.
    fun caida(desde: Double = 2_010.0, hasta: Double = 1_454.0, enS: Double = 9.0): (Double) -> Double =
        { s -> desde + (hasta - desde) * s.coerceAtMost(enS) / enS }

    // Acelera a 3 000 rpm entre 0,5 y 1,5 s, suelta, baja hasta el valle a los 2,2 s y vuelve al mínimo a los 3 s.
    fun retornoSano(minimo: Double = 1_400.0, valle: Double = 1_200.0): (Double) -> Double = { s ->
        when {
            s < 0.5 -> minimo
            s < 1.5 -> recta(s, 0.5, 1.5, minimo, 3_000.0)
            s < 2.2 -> recta(s, 1.5, 2.2, 3_000.0, valle)
            s < 3.0 -> recta(s, 2.2, 3.0, valle, minimo)
            else -> minimo
        }
    }

    fun retornoQueSeApaga(minimo: Double = 1_454.0): (Double) -> Double = { s ->
        when {
            s < 0.5 -> minimo
            s < 1.5 -> recta(s, 0.5, 1.5, minimo, 3_000.0)
            s < 2.2 -> recta(s, 1.5, 2.2, 3_000.0, 900.0)
            s < 2.6 -> recta(s, 2.2, 2.6, 900.0, 0.0)
            else -> 0.0
        }
    }

    fun sinAcelerar(minimo: Double = 1_400.0): (Double) -> Double = { _ -> minimo }

    private fun recta(s: Double, desde: Double, hasta: Double, v0: Double, v1: Double): Double =
        v0 + (v1 - v0) * (s - desde) / (hasta - desde)

    private fun paso(desdeMs: Long, duracionMs: Long, rpm: (Double) -> Double, tpsPct: Double?, ectC: Double?): List<MuestraCaptura> {
        val n = (duracionMs * HZ / 1_000).toInt()
        return (0..n).flatMap { i ->
            val tMs = desdeMs + i * 1_000L / HZ
            listOfNotNull(
                muestra(tMs, AnalizadorMinimo.PID_RPM, rpm(i.toDouble() / HZ), i),
                tpsPct?.let { muestra(tMs, AnalizadorMinimo.PID_TPS, if (i % 2 == 0) it else it + 0.39, i) },
                ectC?.let { muestra(tMs, AnalizadorMinimo.PID_ECT, it, i) },
            )
        }
    }

    private fun muestra(tMs: Long, pid: String, valor: Double, lote: Int) =
        MuestraCaptura(seq = 0, tMicros = tMs * 1_000, pid = pid, valor = valor, lote = lote.toLong(), latenciaMs = 60)
}
