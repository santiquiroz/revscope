package com.revscope.core.obd.taller.pruebas

import androidx.annotation.StringRes
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.telemetry.captura.LimitesCaptura
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

// De dónde salen las muestras: la captura rápida de PIDs o la ráfaga de AT RV (el voltaje lo responde el ELM).
enum class FuenteMuestras { PIDS, VOLTAJE_ADAPTADOR }

// [analizarSiSeCortaEn]: pasos en los que perder el enlace es parte de lo medido (el arranque de la prueba de
// batería): se analiza lo capturado en vez de dar la prueba por fallida. [usaAmbiente]: pide al teléfono su
// barómetro y su altitud al terminar.
data class DefinicionPrueba(
    val tipo: TipoPrueba,
    val pids: List<String>,
    val pidsOpcionales: List<String> = emptyList(),
    val precondiciones: List<Precondicion>,
    val pasos: List<PasoPrueba>,
    val analizar: (DatosPrueba, Map<String, BandaReferencia>, StringResolver) -> ResultadoPrueba,
    val fuente: FuenteMuestras = FuenteMuestras.PIDS,
    val analizarSiSeCortaEn: Set<String> = emptySet(),
    val usaAmbiente: Boolean = false,
) {
    init {
        require(pids.isNotEmpty()) { "La prueba $tipo necesita al menos un PID" } // i18n-ignore
        require(pasos.isNotEmpty()) { "La prueba $tipo necesita al menos un paso" } // i18n-ignore
    }

    fun titulo(resolver: StringResolver): String = tipo.titulo(resolver)

    // Los pasos con [PasoPrueba.tituloRes] quedan con su título en el idioma del resolutor.
    fun resuelta(resolver: StringResolver): DefinicionPrueba = copy(pasos = pasos.map { it.copy(titulo = it.titulo(resolver)) })

    // El PID que se muestra en vivo en cada paso: el del paso si lo dice, si no el primero de la prueba.
    fun pidPrincipal(paso: PasoPrueba): String = paso.pidPrincipal ?: pids.first()

    // Cada paso con su tope más un margen para posicionar el acelerador antes de tocar «Listo».
    val duracionMaximaMs: Long
        get() = pasos.sumOf { it.modo.limiteMs + MARGEN_POR_PASO_MS }.coerceAtMost(LimitesCaptura.MAX_DURACION_MS)

    private companion object {
        const val MARGEN_POR_PASO_MS = 2 * 60_000L
    }
}

// [tituloRes], si está, manda sobre [titulo]: el catálogo no tiene resolutor y la definición se resuelve al usarla.
data class PasoPrueba(
    val clave: String,
    val titulo: String = "",
    val instruccion: String,
    val modo: ModoPaso,
    val descartarInicioMs: Long = 1_000,
    val terminarCuando: CriterioFin? = null,
    val pidPrincipal: String? = null,
    @StringRes val tituloRes: Int? = null,
) {
    fun titulo(resolver: StringResolver): String = tituloRes?.let { resolver.get(it) } ?: titulo
}

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
