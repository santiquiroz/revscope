package com.revscope.core.obd.taller.multimetro

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

// La unidad va con espacio duro: en una columna angosta «5 V» no se parte en dos renglones.
enum class FuncionCable(val etiqueta: String) {
    REF_5V("Referencia 5 V"), // i18n-ignore
    MASA("Masa"), // i18n-ignore
    SENAL("Señal"), // i18n-ignore
    ALIMENTACION_12V("Alimentación 12 V"), // i18n-ignore
    CALEFACTOR("Calefactor"), // i18n-ignore
    OTRO("Otro"); // i18n-ignore

    fun etiqueta(resolver: StringResolver): String = resolver.get(
        when (this) {
            REF_5V -> R.string.obd_multimeter_wire_reference
            MASA -> R.string.obd_multimeter_wire_earth
            SENAL -> R.string.obd_multimeter_wire_signal
            ALIMENTACION_12V -> R.string.obd_multimeter_wire_power
            CALEFACTOR -> R.string.obd_multimeter_wire_heater
            OTRO -> R.string.obd_multimeter_wire_other
        },
    )
}
