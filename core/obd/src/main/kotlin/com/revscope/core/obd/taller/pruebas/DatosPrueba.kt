package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.telemetry.captura.MuestraCaptura

data class SegmentoPaso(val clave: String, val inicioMs: Long, val finMs: Long, val descartarInicioMs: Long = 0) {
    val utilDesdeMs: Long get() = (inicioMs + descartarInicioMs).coerceAtMost(finMs)
    val utilMs: Long get() = finMs - utilDesdeMs
}

data class Punto(val tMs: Long, val valor: Double)

// La referencia con la que el % del PID se convierte a voltios (5,0 V típico, o la medida con el multímetro).
data class ReferenciaVoltaje(val voltios: Double, val origen: String) {
    init {
        require(voltios in MIN_V..MAX_V) { "La referencia debe estar entre $MIN_V y $MAX_V V" }
    }

    fun aVoltios(porcentaje: Double): Double = porcentaje * voltios / 100.0

    fun aPorcentaje(voltiosMedidos: Double): Double = voltiosMedidos * 100.0 / voltios

    companion object {
        const val MIN_V = 3.0
        const val MAX_V = 5.5
        val TIPICA = ReferenciaVoltaje(5.0, "Típico (editable)")
        fun editada(voltios: Double) = ReferenciaVoltaje(voltios, "Editado por ti")
    }
}

data class DatosPrueba(
    val tipo: TipoPrueba,
    val muestras: List<MuestraCaptura>,
    val segmentos: List<SegmentoPaso>,
    val vref: ReferenciaVoltaje = ReferenciaVoltaje.TIPICA,
) {
    fun segmento(clave: String): SegmentoPaso? = segmentos.firstOrNull { it.clave == clave }

    // Solo lo útil del paso: sin el asentamiento inicial.
    fun serie(pid: String, clave: String): List<Punto> {
        val seg = segmento(clave) ?: return emptyList()
        return muestras.asSequence()
            .filter { it.pid == pid }
            .map { Punto(it.tMicros / 1_000, it.valor) }
            .filter { it.tMs in seg.utilDesdeMs..seg.finMs }
            .sortedBy { it.tMs }
            .toList()
    }

    // Lo útil de varios pasos seguidos, como una sola serie en el tiempo de la captura.
    fun serie(pid: String, claves: List<String>): List<Punto> = claves.flatMap { serie(pid, it) }.sortedBy { it.tMs }
}
