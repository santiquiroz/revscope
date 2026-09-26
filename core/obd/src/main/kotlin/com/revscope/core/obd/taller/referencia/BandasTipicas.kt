package com.revscope.core.obd.taller.referencia

import com.revscope.core.data.db.entities.VehicleType

object ClavesBanda {
    const val TPS_CERRADO_V = "TPS_CERRADO_V"
    const val TPS_FONDO_V = "TPS_FONDO_V"
    const val TPS_RECORRIDO_MIN_V = "TPS_RECORRIDO_MIN_V"
    const val TPS_RUIDO_MAX_V = "TPS_RUIDO_MAX_V"
    const val TPS_REPETIBILIDAD_V = "TPS_REPETIBILIDAD_V"
    const val TPS_LINEALIDAD = "TPS_LINEALIDAD"
    const val REF_5V_V = "REF_5V_V"
    const val MASA_V = "MASA_V"
    const val INYECTOR_OHM = "INYECTOR_OHM"
    const val MINIMO_RPM = "MINIMO_RPM"
    const val MINIMO_DERIVA_MAX = "MINIMO_DERIVA_MAX"
    const val MINIMO_DESVIACION_MAX = "MINIMO_DESVIACION_MAX"
    const val RETORNO_VALLE_MIN = "RETORNO_VALLE_MIN"
    const val MOTOR_FRIO_ECT_IAT_DELTA_C = "MOTOR_FRIO_ECT_IAT_DELTA_C"
    const val BATERIA_CONTACTO_V = "BATERIA_CONTACTO_V"
    const val BATERIA_CONTACTO_CON_LUCES_V = "BATERIA_CONTACTO_CON_LUCES_V"
    const val ARRANQUE_MIN_V = "ARRANQUE_MIN_V"
    const val CARGA_V = "CARGA_V"
    const val SOBRECARGA_MAX_V = "SOBRECARGA_MAX_V"
    const val MAP_KOEO_VS_BARO_KPA = "MAP_KOEO_VS_BARO_KPA"
    const val CHEQUEO_AJUSTE_DELTA_PP = "CHEQUEO_AJUSTE_DELTA_PP"
    const val CHEQUEO_VOLTAJE_DELTA_V = "CHEQUEO_VOLTAJE_DELTA_V"
}

// Valores típicos de la industria, no del fabricante: se muestran como «Típico (editable)».
object BandasTipicas {

    fun para(tipo: VehicleType): Map<String, BandaReferencia> =
        (comunes + minimoPara(tipo)).associateBy { it.clave }

    private fun minimoPara(tipo: VehicleType): BandaReferencia = when (tipo) {
        VehicleType.MOTORCYCLE -> tipica(ClavesBanda.MINIMO_RPM, 1_200.0, 1_700.0, "rpm")
        VehicleType.CAR -> tipica(ClavesBanda.MINIMO_RPM, 600.0, 900.0, "rpm")
    }

    private val comunes = listOf(
        tipica(ClavesBanda.TPS_CERRADO_V, 0.3, 1.0, "V"),
        tipica(ClavesBanda.TPS_FONDO_V, 3.8, 4.8, "V"),
        tipica(ClavesBanda.TPS_RECORRIDO_MIN_V, 3.0, null, "V"),
        tipica(ClavesBanda.TPS_RUIDO_MAX_V, null, 0.06, "V"),
        tipica(ClavesBanda.TPS_REPETIBILIDAD_V, null, 0.10, "V"),
        tipica(ClavesBanda.TPS_LINEALIDAD, 0.2, 0.8, "proporción"),
        tipica(ClavesBanda.REF_5V_V, 4.8, 5.2, "V"),
        tipica(ClavesBanda.MASA_V, 0.0, 0.1, "V"),
        tipica(ClavesBanda.INYECTOR_OHM, 10.0, 16.0, "Ω"),
        tipica(ClavesBanda.MINIMO_DERIVA_MAX, null, 10.0, "rpm/s"),
        tipica(ClavesBanda.MINIMO_DESVIACION_MAX, null, 50.0, "rpm"),
        tipica(ClavesBanda.RETORNO_VALLE_MIN, 70.0, null, "% del mínimo"),
        tipica(ClavesBanda.MOTOR_FRIO_ECT_IAT_DELTA_C, null, 5.0, "°C"),
        tipica(ClavesBanda.BATERIA_CONTACTO_V, 12.4, null, "V"),
        tipica(ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V, 12.2, null, "V"),
        tipica(ClavesBanda.ARRANQUE_MIN_V, 9.6, null, "V"),
        tipica(ClavesBanda.CARGA_V, 13.5, 14.5, "V"),
        tipica(ClavesBanda.SOBRECARGA_MAX_V, null, 15.0, "V"),
        tipica(ClavesBanda.MAP_KOEO_VS_BARO_KPA, null, 3.0, "kPa"),
        tipica(ClavesBanda.CHEQUEO_AJUSTE_DELTA_PP, null, 5.0, "pp"),
        tipica(ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V, null, 0.5, "V"),
    )

    private fun tipica(clave: String, min: Double?, max: Double?, unidad: String) =
        BandaReferencia(clave, min, max, unidad, OrigenBanda.TIPICO)
}
