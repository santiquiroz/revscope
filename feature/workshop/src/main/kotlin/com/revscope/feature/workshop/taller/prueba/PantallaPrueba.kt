package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.grafica.PidsPosicion
import com.revscope.core.obd.taller.pruebas.DefinicionPrueba
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FuenteMuestras
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.pruebas.ResultadoPrecondicion
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.referencia.BandaReferencia

data class EntradaPantalla(
    val estado: EstadoPrueba,
    val tipoElegido: TipoPrueba? = null,
    val precondicionesVivas: List<ResultadoPrecondicion>? = null,
    val serieVivo: SerieVivo = emptyList(),
    val vref: ReferenciaVoltaje = ReferenciaVoltaje.TIPICA,
    val bandas: Map<String, BandaReferencia> = emptyMap(),
)

// De lo que publica el controlador (compartido con el MCP) a lo que pinta la pantalla.
internal class PantallaPrueba(
    private val definicion: (TipoPrueba) -> DefinicionPrueba?,
    private val disponibles: List<TipoPrueba>,
) {

    fun fase(entrada: EntradaPantalla): FasePantalla = when (val e = entrada.estado) {
        EstadoPrueba.Inactiva -> inactiva(entrada)
        is EstadoPrueba.Verificando -> preparacion(e.tipo, entrada.precondicionesVivas ?: e.precondiciones) ?: elegir()
        is EstadoPrueba.EnPaso -> FasePantalla.Paso(MapeoPaso.de(e, pidVivo(e), entrada.serieVivo, entrada.vref, entrada.bandas))
        is EstadoPrueba.Analizando -> FasePantalla.Analizando(e.tipo)
        is EstadoPrueba.Terminada -> FasePantalla.Resultado(MapeoResultado.de(e))
        is EstadoPrueba.Cancelada -> FasePantalla.Cancelada(e.tipo, e.motivo)
        is EstadoPrueba.Fallida -> FasePantalla.Fallida(e.tipo, e.motivo, e.reintentable)
    }

    fun pidVivo(e: EstadoPrueba.EnPaso): String = definicion(e.tipo)?.pidPrincipal(e.paso).orEmpty()

    private fun inactiva(entrada: EntradaPantalla): FasePantalla {
        val tipo = entrada.tipoElegido ?: return elegir()
        return preparacion(tipo, entrada.precondicionesVivas.orEmpty()) ?: elegir()
    }

    private fun preparacion(tipo: TipoPrueba, precondiciones: List<ResultadoPrecondicion>): FasePantalla.Preparacion? {
        val d = definicion(tipo) ?: return null
        return FasePantalla.Preparacion(
            tipo = tipo,
            descripcion = TextosPrueba.descripcion(tipo),
            pasos = d.pasos.map(TextosPrueba::paso),
            precondiciones = precondiciones.map { ItemPrecondicion(it.texto, it.cumple, it.queHacer, it.aviso) },
            usaVref = d.pids.any(PidsPosicion::es),
            usaDesfase = d.fuente == FuenteMuestras.VOLTAJE_ADAPTADOR,
        )
    }

    private fun elegir() = FasePantalla.Elegir(
        TipoPrueba.entries.map { OpcionPrueba(it, TextosPrueba.descripcion(it), it in disponibles) },
    )
}
