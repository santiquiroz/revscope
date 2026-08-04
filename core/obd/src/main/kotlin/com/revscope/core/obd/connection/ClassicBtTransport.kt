package com.revscope.core.obd.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
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
) : Transport {

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val ioMutex = Mutex()
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
            inputStream = btSocket.inputStream
            outputStream = btSocket.outputStream

            val name = device.name ?: deviceAddress
            _state.value = ConnectionState.Connected(name)
            Timber.i("ClassicBT connected to $name ($deviceAddress)")
        }.onFailure { e ->
            _state.value = ConnectionState.Error(e.message ?: "Connection failed")
            Timber.e(e, "ClassicBT connection failed for $deviceAddress")
            cleanupSocket()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectWithFallback(device: BluetoothDevice): BluetoothSocket {
        val sppSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        val sppResult = runCatching { connectWithWatchdog(sppSocket) }
        if (sppResult.isSuccess) return sppSocket

        // Cheap ELM327 clones often have broken SDP records — retry on raw channel 1
        Timber.w(sppResult.exceptionOrNull(), "SPP connect failed, retrying via reflection channel $FALLBACK_RFCOMM_CHANNEL")
        val fallbackSocket = createFallbackSocket(device)
            ?: throw sppResult.exceptionOrNull() ?: IOException("SPP connect failed")
        connectWithWatchdog(fallbackSocket)
        return fallbackSocket
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
            if (!watchdog.isActive) throw IOException("Connect timeout after ${CONNECT_TIMEOUT_MS} ms", e)
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
        val out = outputStream ?: throw IOException("Not connected — cannot send: $command")
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
        val input = inputStream ?: throw IOException("Not connected — cannot receive")
        val buffer = StringBuilder()
        val chunk = ByteArray(CHUNK_SIZE)
        val deadline = System.currentTimeMillis() + timeoutMs
        var pollDelayMs = READ_POLL_MIN_MS

        while (true) {
            val available = input.available()
            if (available > 0) {
                val read = input.read(chunk, 0, minOf(available, CHUNK_SIZE))
                if (read == -1) throw IOException("Stream closed by remote device")
                if (read > 0) {
                    buffer.append(String(chunk, 0, read, Charsets.US_ASCII))
                    if (buffer.contains(PROMPT_CHAR)) break
                    pollDelayMs = READ_POLL_MIN_MS
                }
            } else {
                delay(pollDelayMs)
                pollDelayMs = minOf(pollDelayMs * 2, READ_POLL_MAX_MS)
            }
            if (System.currentTimeMillis() >= deadline) {
                throw IOException(
                    "Read timeout after ${timeoutMs} ms without prompt; partial: \"${buffer.trim()}\""
                )
            }
        }

        buffer.toString().also { Timber.v("RX: ${it.trimEnd()}") }
    }

    override suspend fun exchange(command: String, timeoutMs: Long): String = ioMutex.withLock {
        withContext(Dispatchers.IO) { drainStaleInput() }
        send(command)
        receive(timeoutMs)
    }

    override suspend fun targetedExchange(
        requestHeader: String,
        request: String,
        timeoutMs: Long,
    ): String = ioMutex.withLock {
        withContext(Dispatchers.IO) { drainStaleInput() }
        try {
            send("AT H1\r"); receive(AT_RESTORE_TIMEOUT_MS)
            send("AT SH $requestHeader\r"); receive(AT_RESTORE_TIMEOUT_MS)
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
                    send("AT SH $FUNCTIONAL_HEADER_11BIT\r"); receive(AT_RESTORE_TIMEOUT_MS)
                    send("AT H0\r"); receive(AT_RESTORE_TIMEOUT_MS)
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

    private fun cleanupSocket() {
        runCatching { outputStream?.close() }.onFailure { Timber.w(it, "Error closing output") }
        runCatching { inputStream?.close() }.onFailure { Timber.w(it, "Error closing input") }
        runCatching { socket?.close() }.onFailure { Timber.w(it, "Error closing socket") }
        outputStream = null
        inputStream = null
        socket = null
    }
}
