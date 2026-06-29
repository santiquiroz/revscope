package com.revscope.core.obd.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private const val READ_TIMEOUT_MS = 3_000L
private const val READ_POLL_DELAY_MS = 10L
private const val CHUNK_SIZE = 256
private const val PROMPT_CHAR = '>'

/**
 * Bluetooth Classic RFCOMM transport for SPP-based ELM327 adapters (e.g. Vgate iCar Pro 2S).
 *
 * Thread safety: all operations are dispatched on Dispatchers.IO. Do not call methods
 * from multiple coroutines concurrently without external synchronization.
 */
class ClassicBtTransport(
    private val bluetoothAdapter: BluetoothAdapter,
    private val deviceAddress: String,
) : Transport {

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

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

            val btSocket: BluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            btSocket.connect()

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
     * Reads bytes from the adapter until the ELM327 prompt character '>' is received
     * or the read timeout (3 s) elapses.
     *
     * Handles both space-separated ("41 0C 0F A0 >") and compact ("410C0FA0>") formats
     * because the caller (ResponseParser) normalises whitespace.
     */
    override suspend fun receive(): String = withContext(Dispatchers.IO) {
        val input = inputStream ?: throw IOException("Not connected — cannot receive")
        val buffer = StringBuilder()
        val chunk = ByteArray(CHUNK_SIZE)
        val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            val available = input.available()
            if (available > 0) {
                val toRead = minOf(available, CHUNK_SIZE)
                val read = input.read(chunk, 0, toRead)
                if (read > 0) {
                    buffer.append(String(chunk, 0, read, Charsets.US_ASCII))
                    if (buffer.contains(PROMPT_CHAR)) break
                }
            } else {
                delay(READ_POLL_DELAY_MS)
            }
        }

        buffer.toString().also { Timber.v("RX: ${it.trimEnd()}") }
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
