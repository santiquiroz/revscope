package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.telemetry.captura.LimitesCaptura
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

data class DefinicionPrueba(
    val tipo: TipoPrueba,
    val pids: List<String>,
    val pidsOpcionales: List<String> = emptyList(),
    val precondiciones: List<Precondicion>,
    val pasos: List<PasoPrueba>,
    val analizar: (DatosPrueba, Map<String, BandaReferencia>) -> ResultadoPrueba,
) {
    init {
        require(pids.isNotEmpty()) { "La prueba $tipo necesita al menos un PID" }
        require(pasos.isNotEmpty()) { "La prueba $tipo necesita al menos un paso" }
    }

    val titulo: String get() = tipo.titulo

    // Cada paso con su tope más un margen para posicionar el acelerador antes de tocar «Listo».
    val duracionMaximaMs: Long
        get() = pasos.sumOf { it.modo.limiteMs + MARGEN_POR_PASO_MS }.coerceAtMost(LimitesCaptura.MAX_DURACION_MS)

    private companion object {
        const val MARGEN_POR_PASO_MS = 2 * 60_000L
    }
}

data class PasoPrueba(
    val clave: String,
    val titulo: String,
    val instruccion: String,
    val modo: ModoPaso,
    val descartarInicioMs: Long = 1_000,
    val terminarCuando: CriterioFin? = null,
)

sealed interface ModoPaso {
    val limiteMs: Long

    // Posicionar → «Listo» → cuenta regresiva.
    data class Sostener(val ms: Long) : ModoPaso {
        override val limiteMs: Long get() = ms
    }

    data class Grabar(val ms: Long) : ModoPaso {
        override val limiteMs: Long get() = ms
    }

    // Hasta «Terminar», el criterio del paso o el tope.
    data class GrabarHasta(val maxMs: Long) : ModoPaso {
        override val limiteMs: Long get() = maxMs
    }

    // P. ej. «Arranca ahora»: hasta el criterio del paso (el evento), «Terminar» o el tope.
    data class Accion(val maxMs: Long) : ModoPaso {
        override val limiteMs: Long get() = maxMs
    }
}

fun interface CriterioFin {
    fun cumplido(muestrasDelPaso: List<MuestraCaptura>): Boolean
}
