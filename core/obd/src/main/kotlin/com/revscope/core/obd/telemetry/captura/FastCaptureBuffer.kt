package com.revscope.core.obd.telemetry.captura

data class MuestraCaptura(
    val seq: Long,
    val tMicros: Long,
    val pid: String,
    val valor: Double,
    val lote: Long,
    val latenciaMs: Int,
)

data class PaginaCaptura(val muestras: List<MuestraCaptura>, val seqSiguiente: Long, val perdidas: Long)

/**
 * Anillo de alta tasa con arreglos primitivos (~5 MB a 200 000 muestras, más de 15 min a 200/s).
 * [seq] es monotónico: el MCP pagina con un cursor y [PaginaCaptura.perdidas] > 0 si quedó fuera.
 */
class FastCaptureBuffer(val pids: List<String>, private val capacidad: Int = CAPACIDAD_DEFAULT) {

    private val tMicros = LongArray(capacidad)
    private val valores = DoubleArray(capacidad)
    private val pidIdx = ByteArray(capacidad)
    private val lotes = LongArray(capacidad)
    private val latencias = ShortArray(capacidad)
    private val indicePorPid = pids.withIndex().associate { (i, pid) -> pid to i }

    @Volatile var total: Long = 0L
        private set

    @Synchronized
    fun agregar(tMicros: Long, pid: String, valor: Double, lote: Long, latenciaMs: Int) {
        val idx = indicePorPid[pid] ?: return
        val pos = (total % capacidad).toInt()
        this.tMicros[pos] = tMicros
        valores[pos] = valor
        pidIdx[pos] = idx.toByte()
        lotes[pos] = lote
        latencias[pos] = latenciaMs.coerceIn(0, Short.MAX_VALUE.toInt()).toShort()
        total++
    }

    @Synchronized
    fun leerDesde(seq: Long, max: Int, filtro: Set<String>? = null): PaginaCaptura {
        val masViejo = (total - capacidad).coerceAtLeast(0)
        val desde = maxOf(seq, masViejo).coerceAtLeast(0)
        val perdidas = (masViejo - seq).coerceAtLeast(0)
        val salida = ArrayList<MuestraCaptura>(minOf(max, (total - desde).toInt().coerceAtLeast(0)))
        var s = desde
        while (s < total && salida.size < max) {
            muestraEn(s).takeIf { filtro == null || it.pid in filtro }?.let(salida::add)
            s++
        }
        return PaginaCaptura(salida, s, perdidas)
    }

    @Synchronized
    fun todas(): List<MuestraCaptura> = leerDesde(0, Int.MAX_VALUE).muestras

    private fun muestraEn(seq: Long): MuestraCaptura {
        val pos = (seq % capacidad).toInt()
        return MuestraCaptura(
            seq = seq,
            tMicros = tMicros[pos],
            pid = pids[pidIdx[pos].toInt()],
            valor = valores[pos],
            lote = lotes[pos],
            latenciaMs = latencias[pos].toInt(),
        )
    }

    companion object {
        const val CAPACIDAD_DEFAULT = 200_000
    }
}
