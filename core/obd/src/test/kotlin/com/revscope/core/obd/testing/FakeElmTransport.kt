package com.revscope.core.obd.testing

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * ELM327 falso para tests JVM: serializa como el transporte real, consume tiempo virtual por
 * petición y registra cada intercambio con su comando normalizado ("03", "0101", "ATRV").
 *
 * Con [modelarInterrupcion], una petición OBD cancelada a mitad deja al ELM ocupado como el real:
 * el siguiente comando lo interrumpe y se pierde (responde «STOPPED») y el resto de ese comando
 * llega como línea suelta cuyo «?» se lee, rezagado, como respuesta del comando que sigue.
 * [ejecutados] lista solo lo que el ELM llegó a procesar.
 */
class FakeElmTransport(
    private val latenciaMs: (String) -> Long = { 20L },
    private val reloj: () -> Long = { 0L },
    private val responder: (String) -> String,
) : Transport {

    data class Entrada(val comando: String, val respuesta: String, val tInicioMs: Long = 0L)

    private val mutex = Mutex()
    private val enVuelo = AtomicInteger(0)
    private val _log = mutableListOf<Entrada>()
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Connected("FakeELM"))
    private var fallasDesde: Int? = null

    val log: List<Entrada> get() = synchronized(_log) { _log.toList() }
    val comandos: List<String> get() = log.map { it.comando }

    @Volatile var modelarInterrupcion: Boolean = false
    private var ocupado = false
    private var rezagada: String? = null
    private val _ejecutados = mutableListOf<String>()
    val ejecutados: List<String> get() = synchronized(_ejecutados) { _ejecutados.toList() }

    @Volatile var maxConcurrencia: Int = 0
        private set

    fun fallarDespuesDe(n: Int) {
        fallasDesde = n
    }

    override val isConnected: Boolean = true

    override suspend fun connect(): Result<Unit> = Result.success(Unit)

    override suspend fun disconnect() = Unit

    override suspend fun send(command: String) = Unit

    override suspend fun receive(timeoutMs: Long): String = ">"

    override suspend fun exchange(command: String, timeoutMs: Long): String = mutex.withLock {
        val actual = enVuelo.incrementAndGet()
        maxConcurrencia = maxOf(maxConcurrencia, actual)
        try {
            responderRegistrando(normalizar(command))
        } finally {
            enVuelo.decrementAndGet()
        }
    }

    override suspend fun targetedExchange(requestHeader: String, request: String, timeoutMs: Long): String =
        exchange(request, timeoutMs)

    override fun observeConnectionState(): Flow<ConnectionState> = state

    private suspend fun responderRegistrando(comando: String): String {
        val inicio = reloj()
        if (modelarInterrupcion) respuestaDesincronizada(comando, inicio)?.let { return it }
        esperarLatencia(comando)
        val limite = fallasDesde
        if (limite != null && log.size >= limite) throw IOException("enlace muerto (fake)")
        val respuesta = responder(comando)
        registrar(comando, respuesta, inicio, ejecutado = true)
        return respuesta
    }

    private suspend fun esperarLatencia(comando: String) {
        try {
            delay(latenciaMs(comando))
        } catch (e: CancellationException) {
            if (modelarInterrupcion && !comando.startsWith("AT")) ocupado = true
            throw e
        }
    }

    private suspend fun respuestaDesincronizada(comando: String, inicio: Long): String? {
        if (ocupado) {
            ocupado = false
            rezagada = "?\r\r>"
            delay(latenciaMs(comando))
            registrar(comando, STOPPED, inicio, ejecutado = false)
            return STOPPED
        }
        val previa = rezagada ?: return null
        rezagada = null
        delay(latenciaMs(comando))
        // Su propia respuesta llega después y el transporte la drena antes del siguiente envío.
        responder(comando)
        registrar(comando, previa, inicio, ejecutado = true)
        return previa
    }

    private fun registrar(comando: String, respuesta: String, inicio: Long, ejecutado: Boolean) {
        synchronized(_log) { _log += Entrada(comando, respuesta, inicio) }
        if (ejecutado) synchronized(_ejecutados) { _ejecutados += comando }
    }

    companion object {
        private const val STOPPED = "STOPPED\r\r>"

        fun normalizar(command: String): String = command.filterNot { it.isWhitespace() }.uppercase()
    }
}
