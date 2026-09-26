package com.revscope.core.obd.telemetry

import com.revscope.core.obd.connection.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Exclusión sobre el canal serie a nivel de SECUENCIA. El mutex del transporte ya impide que
 * dos peticiones se mezclen en el cable, pero no que el sondeo se cuele entre los 5-12 comandos
 * de una lectura de diagnóstico. El sondeo toma la compuerta por petición ([sondear]); una
 * concesión ([conceder]) la retiene durante toda la secuencia. Mutex justo (FIFO): la concesión
 * espera como mucho las peticiones de sondeo que ya estaban en cola.
 */
class PollingGate {

    private val canal = Mutex()
    private val _duenoConcesion = MutableStateFlow<String?>(null)
    val duenoConcesion: StateFlow<String?> = _duenoConcesion.asStateFlow()

    suspend fun <T> sondear(block: suspend () -> T): T = canal.withLock { block() }

    suspend fun <T> conceder(owner: String, block: suspend () -> T): T = canal.withLock {
        _duenoConcesion.value = owner
        try {
            block()
        } finally {
            _duenoConcesion.value = null
        }
    }
}

/** Transporte del sondeo periódico: cada intercambio pasa por la [PollingGate]. */
class GatedTransport(
    private val inner: Transport,
    private val gate: PollingGate,
) : Transport by inner {

    override suspend fun exchange(command: String, timeoutMs: Long): String =
        gate.sondear { inner.exchange(command, timeoutMs) }

    override suspend fun targetedExchange(requestHeader: String, request: String, timeoutMs: Long): String =
        gate.sondear { inner.targetedExchange(requestHeader, request, timeoutMs) }
}
