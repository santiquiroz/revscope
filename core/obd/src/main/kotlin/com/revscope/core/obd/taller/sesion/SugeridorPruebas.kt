package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.taller.dtc.AccionGuia
import com.revscope.core.obd.taller.dtc.BaseConocimientoDtc
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import javax.inject.Inject

data class PruebaSugerida(val accion: AccionGuia, val motivos: List<String>) {
    val motivo: String get() = "por ${unirMotivos(motivos)}"

    private fun unirMotivos(lista: List<String>): String =
        if (lista.size <= 1) lista.joinToString() else "${lista.dropLast(1).joinToString()} y ${lista.last()}"
}

// Los códigos van primero (su guía dice qué medir); los síntomas suman motivos. Gana la prueba con más motivos.
class SugeridorPruebas(private val accionesDeCodigo: (String) -> List<AccionGuia>) {

    @Inject
    constructor(base: BaseConocimientoDtc) : this(accionesDeGuia(base))

    fun sugerir(sintomas: Set<Sintoma>, codigos: List<String>, maximo: Int = Int.MAX_VALUE): List<PruebaSugerida> {
        val motivos = LinkedHashMap<AccionGuia, MutableList<String>>()
        codigos.distinct().forEach { codigo -> accionesDeCodigo(codigo).forEach { motivos.agregar(it, codigo) } }
        sintomas.sortedBy { it.ordinal }.forEach { sintoma ->
            accionesDeSintoma(sintoma).forEach { motivos.agregar(it, "«${sintoma.etiqueta}»") }
        }
        return motivos.entries
            .map { (accion, porQue) -> PruebaSugerida(accion, porQue.toList()) }
            .sortedByDescending { it.motivos.size }
            .take(maximo)
    }

    private fun MutableMap<AccionGuia, MutableList<String>>.agregar(accion: AccionGuia, motivo: String) {
        if (accion == AccionGuia.BorrarCodigos) return
        val lista = getOrPut(accion) { mutableListOf() }
        if (motivo !in lista) lista += motivo
    }

    companion object {
        fun desde(base: BaseConocimientoDtc) = SugeridorPruebas(accionesDeGuia(base))

        private fun accionesDeGuia(base: BaseConocimientoDtc): (String) -> List<AccionGuia> =
            { codigo -> base.guia(codigo)?.verificaciones?.mapNotNull { it.accion }.orEmpty() }

        private fun prueba(tipo: TipoPrueba) = AccionGuia.Prueba(tipo)

        private fun multimetro(sensor: SensorMultimetro) = AccionGuia.Multimetro(sensor)

        // Relación síntoma → prueba de §2.2 del diseño del Taller; orientativa, no un diagnóstico.
        fun accionesDeSintoma(sintoma: Sintoma): List<AccionGuia> = when (sintoma) {
            Sintoma.SE_AHOGA_AL_ACELERAR -> listOf(prueba(TipoPrueba.TPS_BARRIDO), multimetro(SensorMultimetro.TPS))
            Sintoma.SE_APAGA_AL_SOLTAR, Sintoma.MINIMO_INESTABLE -> listOf(prueba(TipoPrueba.MINIMO_RETORNO))
            Sintoma.NO_SOSTIENE_MINIMO_FRIO -> listOf(prueba(TipoPrueba.ARRANQUE_FRIO), prueba(TipoPrueba.MINIMO_RETORNO))
            Sintoma.ARRANQUE_DIFICIL_FRIO -> listOf(prueba(TipoPrueba.ARRANQUE_FRIO), prueba(TipoPrueba.BATERIA_CARGA))
            Sintoma.BATERIA_DESCARGA, Sintoma.NO_ARRANCA ->
                listOf(prueba(TipoPrueba.BATERIA_CARGA), multimetro(SensorMultimetro.BATERIA))
            Sintoma.PIERDE_POTENCIA -> listOf(prueba(TipoPrueba.TPS_BARRIDO), prueba(TipoPrueba.MAP_BARO))
            Sintoma.TIRONES -> listOf(prueba(TipoPrueba.TPS_BARRIDO))
            Sintoma.CONSUMO_ALTO -> listOf(prueba(TipoPrueba.MAP_BARO), prueba(TipoPrueba.ARRANQUE_FRIO))
            Sintoma.SOBRECALIENTA -> listOf(multimetro(SensorMultimetro.ECT))
            Sintoma.MIL_ENCENDIDA, Sintoma.OTRO -> emptyList()
        }
    }
}
