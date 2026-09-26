package com.revscope.core.obd.telemetry.captura

/** Últimos [ventanaMs] de cada PID para la gráfica en vivo; se alimenta con las páginas del anillo. */
class VentanaCaptura(private val ventanaMs: Long = VENTANA_GRAFICA_MS) {

    private val porPid = linkedMapOf<String, ArrayDeque<Pair<Long, Double>>>()

    var cursor: Long = 0L
        private set

    fun agregar(pagina: PaginaCaptura) {
        pagina.muestras.forEach { m -> porPid.getOrPut(m.pid) { ArrayDeque() }.addLast(m.tMicros / 1_000 to m.valor) }
        cursor = pagina.seqSiguiente
        podar()
    }

    fun series(): Map<String, List<Pair<Long, Double>>> = porPid.mapValues { it.value.toList() }

    fun reiniciar() {
        porPid.clear()
        cursor = 0L
    }

    private fun podar() {
        val ultimo = porPid.values.mapNotNull { it.lastOrNull()?.first }.maxOrNull() ?: return
        val corte = ultimo - ventanaMs
        porPid.values.forEach { serie -> while (serie.isNotEmpty() && serie.first().first < corte) serie.removeFirst() }
    }

    companion object {
        const val VENTANA_GRAFICA_MS = 10_000L
    }
}

/** Elección de PIDs para la captura: como mucho [LimitesCaptura.MAX_PIDS], persistida como CSV hex. */
object SeleccionPids {

    val PEDAL_Y_MARIPOSA = listOf("49", "4A", "11")

    fun alternar(actual: List<String>, pid: String): List<String> = when {
        pid in actual -> actual - pid
        actual.size >= LimitesCaptura.MAX_PIDS -> actual
        else -> actual + pid
    }

    fun desdeCsv(csv: String?): List<String> =
        csv?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }?.distinct()
            ?.take(LimitesCaptura.MAX_PIDS)
            ?.takeIf { it.isNotEmpty() }
            ?: PEDAL_Y_MARIPOSA

    fun aCsv(pids: List<String>): String = pids.joinToString(",")
}
