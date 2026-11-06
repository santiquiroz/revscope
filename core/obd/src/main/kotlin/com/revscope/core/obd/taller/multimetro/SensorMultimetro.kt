package com.revscope.core.obd.taller.multimetro

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

enum class SensorMultimetro(val titulo: String) {
    TPS("Sensor de posición del acelerador (TPS)"), // i18n-ignore
    MAP("Sensor de presión del múltiple (MAP)"), // i18n-ignore
    ECT("Sensor de temperatura del motor (ECT)"), // i18n-ignore
    IAT("Sensor de temperatura del aire (IAT)"), // i18n-ignore
    INYECTOR("Inyector"), // i18n-ignore
    BATERIA("Batería y carga"); // i18n-ignore

    fun titulo(resolver: StringResolver): String = resolver.get(
        when (this) {
            TPS -> R.string.obd_multimeter_sensor_tps
            MAP -> R.string.obd_multimeter_sensor_map
            ECT -> R.string.obd_multimeter_sensor_ect
            IAT -> R.string.obd_multimeter_sensor_iat
            INYECTOR -> R.string.obd_multimeter_sensor_injector
            BATERIA -> R.string.obd_multimeter_sensor_battery
        },
    )
}
