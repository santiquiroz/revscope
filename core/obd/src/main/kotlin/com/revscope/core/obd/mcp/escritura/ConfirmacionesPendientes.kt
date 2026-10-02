package com.revscope.core.obd.mcp.escritura

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Une la espera de una tool con el toque Permitir/Rechazar que llega por el receiver de la notificación. */
@Singleton
class ConfirmacionesPendientes @Inject constructor() {

    private val pendientes = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()
    private val siguienteId = AtomicInteger(1)

    fun abrir(): Pair<Int, Deferred<Boolean>> {
        val id = siguienteId.getAndIncrement()
        val decision = CompletableDeferred<Boolean>()
        pendientes[id] = decision
        return id to decision
    }

    fun resolver(id: Int, permitido: Boolean): Boolean = pendientes.remove(id)?.complete(permitido) ?: false

    fun descartar(id: Int) {
        pendientes.remove(id)?.cancel()
    }
}
