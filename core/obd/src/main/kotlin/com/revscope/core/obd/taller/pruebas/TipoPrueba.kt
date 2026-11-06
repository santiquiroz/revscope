package com.revscope.core.obd.taller.pruebas

import androidx.annotation.StringRes
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

enum class TipoPrueba(@StringRes val tituloRes: Int) {
    TPS_BARRIDO(R.string.obd_prueba_tps_barrido),
    MINIMO_RETORNO(R.string.obd_prueba_minimo_retorno),
    ARRANQUE_FRIO(R.string.obd_prueba_arranque_frio),
    BATERIA_CARGA(R.string.obd_prueba_bateria_carga),
    MAP_BARO(R.string.obd_prueba_map_baro),
    ;

    fun titulo(resolver: StringResolver): String = resolver.get(tituloRes)
}
