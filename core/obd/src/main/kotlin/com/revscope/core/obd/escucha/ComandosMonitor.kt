package com.revscope.core.obd.escucha

import com.revscope.core.obd.connection.Transport

object ComandosMonitor {
    val PREPARACION = listOf("AT H1\r", "AT S0\r", "AT CAF0\r")
    const val SILENCIOSO = "AT CSM1\r"
    const val MONITOR = "AT MA"
    val RESTAURACION = listOf("AT CAF1\r", "AT AR\r", "AT S0\r", "AT H0\r")

    suspend fun preparar(bt: Transport) {
        for (comando in PREPARACION) bt.exchange(comando)
    }

    suspend fun restaurar(bt: Transport) {
        for (comando in RESTAURACION) runCatching { bt.exchange(comando) }
    }
}
