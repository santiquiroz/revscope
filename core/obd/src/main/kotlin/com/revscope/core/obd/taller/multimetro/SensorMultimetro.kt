package com.revscope.core.obd.taller.multimetro

enum class SensorMultimetro(val titulo: String) {
    TPS("Sensor de posición del acelerador (TPS)"),
    MAP("Sensor de presión del múltiple (MAP)"),
    ECT("Sensor de temperatura del motor (ECT)"),
    IAT("Sensor de temperatura del aire (IAT)"),
    INYECTOR("Inyector"),
    BATERIA("Batería y carga"),
}
