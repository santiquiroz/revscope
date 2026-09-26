package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.obd.taller.grafica.EstadoVref
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.sesion.Veredicto

data class PruebaGuiadaUi(
    val fase: FasePantalla,
    val vehiculo: String? = null,
    val voz: Boolean = true,
    val referencia: EstadoVref = EstadoVref(ReferenciaVoltaje.TIPICA, null, null),
    val dialogo: DialogoPrueba? = null,
    val ocupado: Boolean = false,
    val mensaje: String? = null,
) {
    val titulo: String get() = fase.tipo?.titulo ?: "Pruebas guiadas"
    val vref: ReferenciaVoltaje get() = referencia.usada
    val enCurso: Boolean get() = fase is FasePantalla.Paso || fase is FasePantalla.Analizando
}

enum class DialogoPrueba { CANCELAR, VREF }

data class OpcionPrueba(val tipo: TipoPrueba, val descripcion: String, val disponible: Boolean)

data class ItemPrecondicion(val texto: String, val cumple: Boolean, val queHacer: String?, val aviso: Boolean = false)

// [principal] en grande; [secundario] (los voltios de un PID de posición) debajo, si lo hay.
data class ValorVivoUi(val etiqueta: String, val principal: String, val secundario: String? = null)

data class PasoUi(
    val tipo: TipoPrueba,
    val indice: Int,
    val total: Int,
    val titulo: String,
    val instruccion: String,
    val subtexto: String,
    val fase: FasePaso,
    val restanteS: Int?,
    val duracionS: Int,
    val fraccionRestante: Float?,
    val vivo: ValorVivoUi?,
    val grafica: ModeloGrafica,
    val tituloGrafica: String,
    val leyendaGrafica: List<String>,
    val anuncio: String,
    val accionPrincipal: String?,
    val grabados: Int,
) {
    // Con el primer paso aún sin grabar no hay nada que perder: cancelar no pregunta.
    val confirmarAlCancelar: Boolean get() = grabados > 0 || fase != FasePaso.POSICIONANDO
}

data class BandaPesaUi(val desdeV: Double, val hastaV: Double, val texto: String)

data class PesaUi(
    val titulo: String,
    val valores: String,
    val minV: Double,
    val maxV: Double,
    val mediaV: Double,
    val escalaV: Double,
    val banda: BandaPesaUi?,
    val nivel: Veredicto,
    val descripcion: String,
)

data class GraficaResultadoUi(val titulo: String, val modelo: ModeloGrafica, val leyenda: List<String>)

data class ComprobacionUi(val texto: String, val cumple: Boolean)

data class ResultadoUi(
    val tipo: TipoPrueba,
    val veredicto: Veredicto,
    val titulo: String,
    val interpretacion: String,
    val hallazgos: List<String>,
    val siguientePaso: String?,
    val pesas: List<PesaUi>,
    val comprobaciones: List<ComprobacionUi>,
    val medidas: List<String>,
    val graficas: List<GraficaResultadoUi>,
    val referencia: String,
    val codigoGuia: String?,
    val guardado: Boolean,
)

sealed interface FasePantalla {
    val tipo: TipoPrueba?

    data class Elegir(val opciones: List<OpcionPrueba>) : FasePantalla {
        override val tipo: TipoPrueba? get() = null
    }

    data class Preparacion(
        override val tipo: TipoPrueba,
        val descripcion: String,
        val pasos: List<String>,
        val precondiciones: List<ItemPrecondicion>,
        val usaVref: Boolean = true,
    ) : FasePantalla {
        val listas: Boolean get() = precondiciones.isNotEmpty() && precondiciones.all { it.cumple }
    }

    data class Paso(val paso: PasoUi) : FasePantalla {
        override val tipo: TipoPrueba get() = paso.tipo
    }

    data class Analizando(override val tipo: TipoPrueba) : FasePantalla

    data class Resultado(val resultado: ResultadoUi) : FasePantalla {
        override val tipo: TipoPrueba get() = resultado.tipo
    }

    data class Cancelada(override val tipo: TipoPrueba, val motivo: String) : FasePantalla

    data class Fallida(override val tipo: TipoPrueba, val motivo: String, val reintentable: Boolean) : FasePantalla
}
