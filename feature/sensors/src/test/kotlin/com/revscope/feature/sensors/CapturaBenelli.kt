package com.revscope.feature.sensors

// 30 s de captura rápida de la Benelli TNT 150i: el TPS de cerrado (2,35 %) a medio (9,0-9,4 %) y las rpm
// subiendo de 1454 a 2010, a 10 Hz. Los tiempos van en ms de la captura.
internal object CapturaBenelli {

    private const val N = 300
    private const val PASO_MS = 100L

    fun nombre(pid: String): String = when (pid) {
        "11" -> "Posición de la mariposa"
        "0C" -> "RPM"
        "49" -> "Posición del pedal D"
        else -> pid
    }

    fun unidad(pid: String): String = when (pid) {
        "11" -> "%"
        "0C" -> "rpm"
        else -> ""
    }

    fun series(conRpm: Boolean = true): Map<String, List<Pair<Long, Double>>> =
        if (conRpm) mapOf("11" to tps(), "0C" to rpm()) else mapOf("11" to tps())

    fun tps(): List<Pair<Long, Double>> = (0 until N).map { i ->
        val byte = when {
            i < 120 -> 6
            i < 140 -> 6 + (i - 119)
            else -> if (i % 2 == 0) 23 else 24
        }
        i * PASO_MS to byte * 100.0 / 255.0
    }

    fun rpm(): List<Pair<Long, Double>> = (0 until N).map { i -> i * PASO_MS + 50 to 1_454.0 + (2_010.0 - 1_454.0) * i / (N - 1) }
}
