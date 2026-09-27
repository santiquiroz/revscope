package com.revscope.core.obd.taller.multimetro

// La unidad va con espacio duro: en una columna angosta «5 V» no se parte en dos renglones.
enum class FuncionCable(val etiqueta: String) {
    REF_5V("Referencia 5 V"),
    MASA("Masa"),
    SENAL("Señal"),
    ALIMENTACION_12V("Alimentación 12 V"),
    CALEFACTOR("Calefactor"),
    OTRO("Otro"),
}
