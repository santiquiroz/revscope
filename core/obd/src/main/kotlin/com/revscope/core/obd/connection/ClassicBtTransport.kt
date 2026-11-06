package com.revscope.core.obd.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private const val CONNECT_TIMEOUT_MS = 12_000L
// Poll adaptativo: BluetoothSocket no expone timeout de lectura y un read() bloqueante solo
// se desbloquea cerrando el socket — inaceptable, porque un timeout de respuesta debe dejar
// la conexión viva para el retry del caller. El backoff exponencial mantiene latencia baja
// al inicio (2 ms) y reduce ~3× los wakeups del timer en esperas largas.
private const val READ_POLL_MIN_MS = 2L
private const val READ_POLL_MAX_MS = 40L
// Captura rápida: una respuesta que llega a los 35 ms no debe detectarse hasta los ~62 ms.
private const val LOW_LATENCY_READ_POLL_MAX_MS = 4L
private const val CHUNK_SIZE = 256
private const val PROMPT_CHAR = '>'
private const val FALLBACK_RFCOMM_CHANNEL = 1
private const val AT_RESTORE_TIMEOUT_MS = 1_000L
private const val FUNCTIONAL_HEADER_11BIT = "7DF"

/**
 * Bluetooth Classic RFCOMM transport for SPP-based ELM327 adapters (e.g. Vgate iCar Pro 2S).
 *
 * Thread safety: [exchange] serializes send/receive pairs behind an internal mutex —
 * always prefer it over raw [send]/[receive], which have no synchronization.
 */
class ClassicBtTransport(
    private val bluetoothAdapter: BluetoothAdapter,
    private val deviceAddress: String,
    private val resolver: StringResolver,
) : Transport {

    private var socket: BluetoothSocket? = null
    @Volatile private var connectingSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val ioMutex = Mutex()
    @Volatile private var readPollMaxMs = READ_POLL_MAX_MS
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Connecting
        runCatching {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
            // Cancel discovery — it slows down and can interfere with RFCOMM connection
            bluetoothAdapter.cancelDiscovery()

            val btSocket = connectWithFallback(device)

            socket = btSocket
            connectingSocket = null
            inputStream = btSocket.inputStream
            outputStream = btSocket.outputStream

            val name = device.name ?: deviceAddress
            _state.value = ConnectionState.Connected(name)
            Timber.i("ClassicBT connected to $name ($deviceAddress)")
        }.onFailure { e ->
            _state.value = ConnectionState.Error(e.message ?: resolver.get(R.string.obd_connection_failed))
            Timber.e(e, "ClassicBT connection failed for $deviceAddress")
            cleanupSocket()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectWithFallback(device: BluetoothDevice): BluetoothSocket {
        val sppSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        connectingSocket = sppSocket
        try {
            connectWithWatchdog(sppSocket)
            return sppSocket
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            connectingSocket = null
            runCatching { sppSocket.close() }
            Timber.w(e, "SPP connect failed, retrying via reflection channel $FALLBACK_RFCOMM_CHANNEL")
            val fallbackSocket = createFallbackSocket(device)
                ?: throw e
            connectingSocket = fallbackSocket
            connectWithWatchdog(fallbackSocket)
            return fallbackSocket
        }
    }

    /**
     * BluetoothSocket.connect() is a blocking call that can hang indefinitely on
     * flaky adapters. The watchdog closes the socket at the deadline, which forces
     * connect() to abort with an IOException.
     */
    @SuppressLint("MissingPermission")
    private suspend fun connectWithWatchdog(btSocket: BluetoothSocket) = coroutineScope {
        val watchdog = launch {
            delay(CONNECT_TIMEOUT_MS)
            Timber.w("ClassicBT connect watchdog fired after ${CONNECT_TIMEOUT_MS} ms")
            runCatching { btSocket.close() }
        }
        try {
            withContext(Dispatchers.IO) { btSocket.connect() }
        } catch (e: IOException) {
            if (!watchdog.isActive) throw IOException(resolver.get(R.string.obd_connection_connect_timeout, CONNECT_TIMEOUT_MS), e)
            throw e
        } finally {
            watchdog.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    private fun createFallbackSocket(device: BluetoothDevice): BluetoothSocket? = runCatching {
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        method.invoke(device, FALLBACK_RFCOMM_CHANNEL) as BluetoothSocket
    }.onFailure { Timber.w(it, "Reflection fallback socket creation failed") }.getOrNull()

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        Timber.i("ClassicBT disconnecting from $deviceAddress")
        cleanupSocket()
        _state.value = ConnectionState.Disconnected
    }

    override suspend fun send(command: String) = withContext(Dispatchers.IO) {
        val out = outputStream ?: throw IOException(resolver.get(R.string.obd_connection_cannot_send, command))
        out.write(command.toByteArray(Charsets.US_ASCII))
        out.flush()
        Timber.v("TX: ${command.trimEnd()}")
    }

    /**
     * Reads bytes from the adapter until the ELM327 prompt character '>' is received.
     *
     * Throws [IOException] when the remote side closes the stream (EOF) or when
     * [timeoutMs] elapses without seeing the prompt — callers rely on the exception
     * to trigger their retry path; a partial buffer must never be returned as if it
     * were a complete response.
     *
     * Handles both space-separated ("41 0C 0F A0 >") and compact ("410C0FA0>") formats
     * because the caller (ResponseParser) normalises whitespace.
     */
    override suspend fun receive(timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val input = inputStream ?: throw IOException(resolver.get(R.string.obd_connection_cannot_receive))
        val buffer = StringBuilder()
        val chunk = ByteArray(CHUNK_SIZE)
        val deadline = System.currentTimeMillis() + timeoutMs
        var pollDelayMs = READ_POLL_MIN_MS

        while (true) {
            val available = input.available()
            if (available > 0) {
                val read = input.read(chunk, 0, minOf(available, CHUNK_SIZE))
                if (read == -1) throw IOException(resolver.get(R.string.obd_connection_stream_closed))
                if (read > 0) {
                    buffer.append(String(chunk, 0, read, Charsets.US_ASCII))
                    if (buffer.contains(PROMPT_CHAR)) break
                    pollDelayMs = READ_POLL_MIN_MS
                }
            } else {
                delay(pollDelayMs)
                pollDelayMs = minOf(pollDelayMs * 2, readPollMaxMs)
            }
            if (System.currentTimeMillis() >= deadline) {
                throw IOException(resolver.get(R.string.obd_connection_read_timeout, timeoutMs, buffer.trim()))
            }
        }

        buffer.toString().also { Timber.v("RX: ${it.trimEnd()}") }
    }

    override suspend fun exchange(command: String, timeoutMs: Long): String = ioMutex.withLock {
        withContext(Dispatchers.IO) { drainStaleInput() }
        send(command)
        receive(timeoutMs)
    }

    override suspend fun monitorear(
        comando: String,
        duracionMs: Long,
        detener: () -> Boolean,
        alLinea: (String) -> Unit,
    ): String =
        ioMutex.withLock {
            withContext(Dispatchers.IO) { drainStaleInput() }
            val cmd = if (comando.endsWith("\r")) comando else "$comando\r"
            send(cmd)
            withContext(Dispatchers.IO) { leerMonitorHastaPlazo(duracionMs, detener, alLinea) }
        }

    private suspend fun leerMonitorHastaPlazo(
        duracionMs: Long,
        detener: () -> Boolean,
        alLinea: (String) -> Unit,
    ): String {
        val input = inputStream ?: throw IOException(resolver.get(R.string.obd_connection_cannot_monitor))
        val total = StringBuilder()
        val pendiente = StringBuilder()
        val plazo = System.currentTimeMillis() + duracionMs
        var pollDelayMs = READ_POLL_MIN_MS
        while (System.currentTimeMillis() < plazo && !detener()) {
            val available = input.available()
            if (available > 0) {
                val trozo = leerDisponible(input, available)
                    ?: throw IOException(resolver.get(R.string.obd_connection_stream_closed))
                total.append(trozo)
                pendiente.append(trozo)
                val prompt = pendiente.indexOf(PROMPT_CHAR.toString())
                if (prompt >= 0) {
                    CortadorLineasMonitor.entregarRestos(pendiente.substring(0, prompt), alLinea)
                    return total.toString()
                }
                CortadorLineasMonitor.drenarLineas(pendiente, alLinea)
                pollDelayMs = READ_POLL_MIN_MS
            } else {
                delay(pollDelayMs)
                pollDelayMs = minOf(pollDelayMs * 2, readPollMaxMs)
            }
        }
        send("\r")
        val final = receive(AT_RESTORE_TIMEOUT_MS)
        CortadorLineasMonitor.entregarRestos(pendiente.toString() + final.substringBefore(PROMPT_CHAR), alLinea)
        return final
    }

    private fun leerDisponible(input: InputStream, available: Int): String? {
        val chunk = ByteArray(CHUNK_SIZE)
        val read = input.read(chunk, 0, minOf(available, CHUNK_SIZE))
        if (read == -1) return null
        if (read == 0) return ""
        return String(chunk, 0, read, Charsets.US_ASCII)
    }

    override suspend fun targetedExchange(
        requestHeader: String,
        request: String,
        timeoutMs: Long,
    ): String = ioMutex.withLock {
        withContext(Dispatchers.IO) { drainStaleInput() }
        try {
            send("AT H1\r"); receive(AT_RESTORE_TIMEOUT_MS) // i18n-ignore
            send("AT SH $requestHeader\r"); receive(AT_RESTORE_TIMEOUT_MS) // i18n-ignore
            val cmd = if (request.endsWith("\r")) request else "$request\r"
            send(cmd)
            receive(timeoutMs)
        } finally {
            // Restaurar SIEMPRE los defaults de telemetría, aun si la petición
            // agota el tiempo, el módulo no responde o el job se cancela. Sin esto,
            // el header custom quedaría fijo y la telemetría saldría dirigida al
            // módulo equivocado. NonCancellable garantiza que la restauración corra
            // incluso cuando la coroutine ya está cancelada.
            withContext(NonCancellable + Dispatchers.IO) {
                drainStaleInput()
                runCatching {
                    send("AT SH $FUNCTIONAL_HEADER_11BIT\r"); receive(AT_RESTORE_TIMEOUT_MS) // i18n-ignore
                    send("AT H0\r"); receive(AT_RESTORE_TIMEOUT_MS) // i18n-ignore
                }
            }
        }
    }

    /** Discards bytes left over from a previous timed-out read so they don't corrupt the next response. */
    private fun drainStaleInput() {
        val input = inputStream ?: return
        val chunk = ByteArray(CHUNK_SIZE)
        runCatching {
            var drained = 0
            while (input.available() > 0) {
                val read = input.read(chunk, 0, minOf(input.available(), CHUNK_SIZE))
                if (read <= 0) break
                drained += read
            }
            if (drained > 0) Timber.w("Drained $drained stale bytes before send")
        }
    }

    override fun observeConnectionState(): Flow<ConnectionState> = _state.asStateFlow()

    override suspend fun setLowLatency(enabled: Boolean) {
        readPollMaxMs = if (enabled) LOW_LATENCY_READ_POLL_MAX_MS else READ_POLL_MAX_MS
    }

    private fun cleanupSocket() {
        runCatching { outputStream?.close() }.onFailure { Timber.w(it, "Error closing output") }
        runCatching { inputStream?.close() }.onFailure { Timber.w(it, "Error closing input") }
        runCatching { socket?.close() }.onFailure { Timber.w(it, "Error closing socket") }
        runCatching { connectingSocket?.close() }.onFailure { Timber.w(it, "Error closing connecting socket") }
        outputStream = null
        inputStream = null
        socket = null
        connectingSocket = null
    }
}
