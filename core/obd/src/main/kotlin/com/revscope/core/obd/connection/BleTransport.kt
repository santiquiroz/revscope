package com.revscope.core.obd.connection

import android.content.Context
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.WriteType
import com.welie.blessed.ConnectionState as BleConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import java.util.UUID

private const val BLE_CONNECT_TIMEOUT_MS = 15_000L
private const val PROMPT_CHAR = '>'
private const val AT_RESTORE_TIMEOUT_MS = 1_000L
private const val FUNCTIONAL_HEADER_11BIT = "7DF"
private const val REQUESTED_MTU = 512
private const val RX_POLL_SLICE_MS = 50L

/**
 * Transporte BLE (GATT) para clones ELM327 BLE — Vgate iCar Pro 4.0, VLink, etc.
 *
 * A diferencia de RFCOMM, aquí no hay stream: los bytes llegan por notificaciones
 * GATT (event-driven, cero polling) y se envían escribiendo la característica de
 * escritura. La familia de chip se auto-detecta probando los 6 juegos de UUIDs de
 * [BleUuidSets] contra los servicios descubiertos.
 *
 * Thread safety: mismo contrato que [ClassicBtTransport] — [exchange] serializa
 * pares send/receive tras un mutex interno; ELM327 es half-duplex.
 */
class BleTransport(
    context: Context,
    private val deviceAddress: String,
) : Transport {

    private val central = BluetoothCentralManager(context.applicationContext)
    private var peripheral: BluetoothPeripheral? = null
    private var writeCharacteristic: android.bluetooth.BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: android.bluetooth.BluetoothGattCharacteristic? = null
    private var writeType: WriteType = WriteType.WITHOUT_RESPONSE

    // Bytes de notificaciones GATT → canal ilimitado; receive() los acumula hasta el prompt.
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val rxBuffer = StringBuilder()

    private val ioMutex = Mutex()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    @Volatile private var connected = false

    override val isConnected: Boolean
        get() = connected

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Connecting
        runCatching {
            central.observeConnectionState { p, state ->
                if (p.address == deviceAddress && state == BleConnectionState.DISCONNECTED && connected) {
                    // Caída inesperada del enlace — el session manager reacciona igual que
                    // con Classic BT: circuit breaker + auto-reconexión.
                    connected = false
                    _state.value = ConnectionState.Error("BLE link lost")
                }
            }
            val p = central.getPeripheral(deviceAddress)
            withTimeout(BLE_CONNECT_TIMEOUT_MS) { central.connectPeripheral(p) }
            peripheral = p

            val uuidSet = detectUuidSet(p)
                ?: throw IOException("No ELM327 BLE service found on $deviceAddress (tried ${BleUuidSets.ALL.size} chip families)")
            Timber.i("BleTransport: detected chip family ${uuidSet.name}")

            runCatching { p.requestMtu(REQUESTED_MTU) }
                .onFailure { Timber.w("BleTransport: MTU request failed, staying at default") }

            val writeChar = p.getCharacteristic(UUID.fromString(uuidSet.service), UUID.fromString(uuidSet.writeChar))
                ?: throw IOException("Write characteristic missing for ${uuidSet.name}")
            val notifyChar = p.getCharacteristic(UUID.fromString(uuidSet.service), UUID.fromString(uuidSet.notifyChar))
                ?: throw IOException("Notify characteristic missing for ${uuidSet.name}")
            writeCharacteristic = writeChar
            notifyCharacteristic = notifyChar
            writeType = if (writeChar.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                WriteType.WITHOUT_RESPONSE
            } else {
                WriteType.WITH_RESPONSE
            }

            val observing = p.observe(notifyChar) { value -> incoming.trySend(value) }
            if (!observing) throw IOException("Could not enable notifications for ${uuidSet.name}")

            connected = true
            val name = p.name.ifBlank { deviceAddress }
            _state.value = ConnectionState.Connected(name)
            Timber.i("BleTransport connected to $name ($deviceAddress)")
        }.onFailure { e ->
            connected = false
            _state.value = ConnectionState.Error(e.message ?: "BLE connection failed")
            Timber.e(e, "BleTransport connection failed for $deviceAddress")
            cleanup()
        }.map { }
    }

    /** Primera familia cuyo servicio + característica de escritura existen en el periférico. */
    private fun detectUuidSet(p: BluetoothPeripheral): BleUuidSet? =
        BleUuidSets.ALL.firstOrNull { set ->
            runCatching {
                p.getCharacteristic(UUID.fromString(set.service), UUID.fromString(set.writeChar)) != null
            }.getOrDefault(false)
        }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        Timber.i("BleTransport disconnecting from $deviceAddress")
        connected = false
        cleanup()
        _state.value = ConnectionState.Disconnected
    }

    private suspend fun cleanup() {
        peripheral?.let { p -> runCatching { central.cancelConnection(p) } }
        peripheral = null
        writeCharacteristic = null
        notifyCharacteristic = null
        drainIncoming()
    }

    override suspend fun send(command: String) {
        val p = peripheral ?: throw IOException("Not connected — cannot send: $command")
        val char = writeCharacteristic ?: throw IOException("Not connected — cannot send: $command")
        val payload = command.toByteArray(Charsets.US_ASCII)
        // Comandos ELM son cortos (<20 bytes) pero el MTU default es 23 — trocear por si acaso.
        val chunkSize = (peripheral?.currentMtu ?: 23) - 3
        payload.toList().chunked(chunkSize).forEach { chunk ->
            p.writeCharacteristic(char, chunk.toByteArray(), writeType)
        }
        Timber.v("TX(BLE): ${command.trimEnd()}")
    }

    /**
     * Acumula notificaciones hasta el prompt '>' del ELM327. Event-driven: sin datos,
     * la coroutine queda suspendida en el canal — cero wakeups (mejor que RFCOMM).
     */
    override suspend fun receive(timeoutMs: Long): String {
        if (!connected) throw IOException("Not connected — cannot receive")
        val deadline = System.currentTimeMillis() + timeoutMs

        while (true) {
            val promptIdx = rxBuffer.indexOf(PROMPT_CHAR)
            if (promptIdx >= 0) {
                val response = rxBuffer.substring(0, promptIdx + 1)
                rxBuffer.delete(0, promptIdx + 1)
                Timber.v("RX(BLE): ${response.trimEnd()}")
                return response
            }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                throw IOException(
                    "Read timeout after ${timeoutMs} ms without prompt; partial: \"${rxBuffer.trim()}\""
                )
            }
            // Slice corto para re-chequear connected sin quedar colgado si el enlace muere.
            val chunk = withTimeoutOrNull(minOf(remaining, RX_POLL_SLICE_MS * 10)) { incoming.receive() }
            if (chunk != null) {
                rxBuffer.append(String(chunk, Charsets.US_ASCII))
            } else if (!connected) {
                throw IOException("BLE link lost while receiving")
            }
        }
    }

    override suspend fun exchange(command: String, timeoutMs: Long): String = ioMutex.withLock {
        drainStaleInput()
        send(command)
        receive(timeoutMs)
    }

    override suspend fun targetedExchange(
        requestHeader: String,
        request: String,
        timeoutMs: Long,
    ): String = ioMutex.withLock {
        drainStaleInput()
        try {
            send("AT H1\r"); receive(AT_RESTORE_TIMEOUT_MS)
            send("AT SH $requestHeader\r"); receive(AT_RESTORE_TIMEOUT_MS)
            val cmd = if (request.endsWith("\r")) request else "$request\r"
            send(cmd)
            receive(timeoutMs)
        } finally {
            // Mismo contrato que ClassicBtTransport: restaurar SIEMPRE los defaults de
            // telemetría bajo NonCancellable, aun si la petición falla o se cancela.
            withContext(NonCancellable + Dispatchers.IO) {
                drainStaleInput()
                runCatching {
                    send("AT SH $FUNCTIONAL_HEADER_11BIT\r"); receive(AT_RESTORE_TIMEOUT_MS)
                    send("AT H0\r"); receive(AT_RESTORE_TIMEOUT_MS)
                }
            }
        }
    }

    /** Descarta bytes residuales de una lectura anterior que agotó su timeout. */
    private fun drainStaleInput() {
        val stale = rxBuffer.length + drainIncoming()
        if (stale > 0) Timber.w("BleTransport: drained $stale stale chars before send")
        rxBuffer.setLength(0)
    }

    private fun drainIncoming(): Int {
        var drained = 0
        while (true) {
            val chunk = incoming.tryReceive().getOrNull() ?: break
            drained += chunk.size
            rxBuffer.append(String(chunk, Charsets.US_ASCII))
        }
        return drained
    }

    override fun observeConnectionState(): Flow<ConnectionState> = _state.asStateFlow()
}
