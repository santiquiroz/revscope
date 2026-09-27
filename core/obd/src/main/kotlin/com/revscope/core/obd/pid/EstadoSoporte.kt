package com.revscope.core.obd.pid

sealed interface EstadoSoporte {
    data object Soportado : EstadoSoporte
    data object NoSoportado : EstadoSoporte
    data object Desconocido : EstadoSoporte
}
