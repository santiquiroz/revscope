package com.revscope.feature.workshop.taller.modelo

import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.referencia.BandaReferencia

data class ConocimientoModeloUi(
    val vehiculo: String? = null,
    val modelo: ConocimientoModelo? = null,
    val cargando: Boolean = true,
    val error: String? = null,
    val dialogoCable: EdicionCableUi? = null,
    val mensaje: String? = null,
)

data class EdicionCableUi(
    val sensor: String,
    val funcion: FuncionCable,
    val etiqueta: String,
    val color: String,
)

data class ReferenciasUi(
    val vehiculo: String? = null,
    val modelo: String? = null,
    val bandas: List<BandaReferencia> = emptyList(),
    val cargando: Boolean = true,
    val error: String? = null,
    val dialogo: EdicionBandaUi? = null,
    val mensaje: String? = null,
)

data class EdicionBandaUi(
    val banda: BandaReferencia,
    val minimo: String,
    val maximo: String,
    val error: String? = null,
)
