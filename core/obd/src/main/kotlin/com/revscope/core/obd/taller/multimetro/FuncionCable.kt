package com.revscope.core.obd.taller.multimetro

enum class FuncionCable(val etiqueta: String) {
    REF_5V("Referencia 5 V"),
    MASA("Masa"),
    SENAL("Señal"),
    ALIMENTACION_12V("Alimentación 12 V"),
    CALEFACTOR("Calefactor"),
    OTRO("Otro"),
}
