package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

// Barrido del TPS armado a mano: cada sostenido dura 5 s (el primer segundo se descarta) y el barrido lento 8 s.
// Los valores van en bytes crudos del PID 11 (A × 100 / 255 %), como los entrega la ECU.
object BarridoSintetico {

    const val SOSTENIDO_MS = 5_000L
    const val BARRIDO_MS = 8_000L
    private const val PAUSA_MS = 1_000L

    fun pct(a: Int): Double = a * 100.0 / 255.0

    fun byteDeVoltios(v: Double, vref: Double = 5.0): Int = Math.round(v / vref * 255.0).toInt()

    fun datos(
        cerrado1: List<Int>,
        medio: List<Int>,
        fondo: List<Int>,
        cerrado2: List<Int>,
        barrido: List<Int> = subidaYBajada(cerrado1.first(), fondo.first()),
        hz: Double = 10.0,
        vref: ReferenciaVoltaje = ReferenciaVoltaje.TIPICA,
    ): DatosPrueba {
        val segmentos = mutableListOf<SegmentoPaso>()
        val muestras = mutableListOf<MuestraCaptura>()
        var t = 0L
        listOf(Pasos.CERRADO_1 to cerrado1, Pasos.MEDIO to medio, Pasos.A_FONDO to fondo, Pasos.CERRADO_2 to cerrado2)
            .forEach { (clave, valores) ->
                segmentos += SegmentoPaso(clave, t, t + SOSTENIDO_MS, 1_000)
                muestras += muestrear(t, SOSTENIDO_MS, hz) { i -> valores[i % valores.size] }
                t += SOSTENIDO_MS + PAUSA_MS
            }
        segmentos += SegmentoPaso(Pasos.BARRIDO_LENTO, t, t + BARRIDO_MS, 0)
        val nBarrido = (BARRIDO_MS * hz / 1_000).toInt()
        muestras += muestrear(t, BARRIDO_MS, hz) { i -> barrido[(i * barrido.size / nBarrido).coerceAtMost(barrido.lastIndex)] }
        return DatosPrueba(TipoPrueba.TPS_BARRIDO, muestras.mapIndexed { i, m -> m.copy(seq = i.toLong()) }, segmentos, vref)
    }

    fun subidaYBajada(desde: Int, hasta: Int, pasos: Int = 40): List<Int> {
        val subida = (0..pasos).map { desde + (hasta - desde) * it / pasos }
        return subida + subida.reversed().drop(1)
    }

    // Una muestra por cada «tick» de la tasa: bytes crudos convertidos a %.
    private fun muestrear(desdeMs: Long, duracionMs: Long, hz: Double, valor: (Int) -> Int): List<MuestraCaptura> {
        val n = (duracionMs * hz / 1_000).toInt()
        return (0 until n).map { i ->
            val tMicros = (desdeMs * 1_000 + i * 1_000_000 / hz).toLong()
            MuestraCaptura(seq = 0, tMicros = tMicros, pid = "11", valor = pct(valor(i)), lote = i.toLong(), latenciaMs = 60)
        }
    }
}
