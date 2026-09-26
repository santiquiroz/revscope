package com.revscope.core.obd.taller.referencia

import com.revscope.core.data.db.entities.VehicleType

// Prioridad: la banda del usuario gana a la del modelo con fuente, y esa a la típica.
object ResolutorBandas {

    fun resolver(clave: String, tipo: VehicleType, bandasModelo: List<BandaReferencia>): BandaReferencia? =
        bandasModelo.filter { it.clave == clave }.maxByOrNull { prioridad(it.origen) }
            ?: BandasTipicas.para(tipo)[clave]

    fun resolverTodas(tipo: VehicleType, bandasModelo: List<BandaReferencia>): Map<String, BandaReferencia> {
        val claves = BandasTipicas.para(tipo).keys + bandasModelo.map { it.clave }
        return claves.associateWith { clave -> checkNotNull(resolver(clave, tipo, bandasModelo)) }
    }

    private fun prioridad(origen: OrigenBanda): Int = when (origen) {
        OrigenBanda.TIPICO -> 0
        OrigenBanda.FUENTE -> 1
        OrigenBanda.USUARIO -> 2
    }
}
