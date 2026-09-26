package com.revscope.core.obd.taller.pruebas

enum class TipoPrueba(val titulo: String) {
    TPS_BARRIDO("Barrido del TPS"),
    MINIMO_RETORNO("Mínimo y retorno"),
    ARRANQUE_FRIO("Arranque en frío"),
    BATERIA_CARGA("Batería y carga"),
    MAP_BARO("MAP contra presión barométrica"),
}
