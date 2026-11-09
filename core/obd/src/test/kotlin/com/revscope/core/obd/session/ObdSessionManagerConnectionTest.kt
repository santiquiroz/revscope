package com.revscope.core.obd.session

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.data.db.dao.LapDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.mcp.McpActivityTracker
import com.revscope.core.obd.perfilcan.PerfilesCanRepositorio
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.service.TripSummaryNotifier
import com.revscope.core.obd.track.TrackModeEngine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdSessionManagerConnectionTest {

    @Test
    fun `disconnect invalidates a connection that completes afterward`() = runBlocking {
        val fakeTransport = SuspendingTransport()
        val manager = manager().also {
            it.transportFactory = { _, _, _ -> fakeTransport }
        }

        manager.connectToDevice("fake-device")
        withTimeout(2_000) { fakeTransport.connectStarted.await() }

        manager.disconnect()
        fakeTransport.releaseConnect.complete(Unit)
        withTimeout(2_000) { fakeTransport.connectCompleted.await() }
        withTimeout(2_000) {
            while (!fakeTransport.closed || fakeTransport.disconnectCount < 2) delay(10)
        }

        assertTrue(fakeTransport.closed)
        assertTrue(fakeTransport.disconnectCount >= 2)
        assertEquals(0, fakeTransport.exchangeCount)
        assertTrue(manager.connectionState.value is ConnectionState.Disconnected)
    }

    private fun manager() = ObdSessionManager(
        appContext = mockk<Context>(relaxed = true),
        bluetoothAdapter = mockk<BluetoothAdapter>(relaxed = true),
        registry = mockk<PidRegistry>(relaxed = true),
        sessionDao = mockk<SessionDao>(relaxed = true),
        telemetryDao = mockk<TelemetryDao>(relaxed = true),
        profileDao = mockk<VehicleProfileDao>(relaxed = true),
        lapDao = mockk<LapDao>(relaxed = true),
        imuDao = mockk<ImuDao>(relaxed = true),
        gpsDao = mockk<GpsDao>(relaxed = true),
        settings = mockk<DataStore<Preferences>>(relaxed = true),
        alertsEngine = mockk<AlertsEngine>(relaxed = true),
        trackModeEngine = mockk<TrackModeEngine>(relaxed = true) { every { lapEvents } returns MutableSharedFlow() },
        tripSummaryNotifier = mockk<TripSummaryNotifier>(relaxed = true),
        mcpActivity = mockk<McpActivityTracker>(relaxed = true),
        perfilesCan = mockk<PerfilesCanRepositorio>(relaxed = true),
        resolver = mockk<StringResolver>(relaxed = true),
    )

    private class SuspendingTransport : Transport {
        val connectStarted = CompletableDeferred<Unit>()
        val releaseConnect = CompletableDeferred<Unit>()
        val connectCompleted = CompletableDeferred<Unit>()
        private val state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
        private val disconnects = AtomicInteger()
        @Volatile var closed = false
            private set
        val disconnectCount: Int get() = disconnects.get()
        @Volatile var exchangeCount = 0
            private set

        override val isConnected: Boolean get() = !closed

        override suspend fun connect(): Result<Unit> {
            connectStarted.complete(Unit)
            withContext(NonCancellable) {
                releaseConnect.await()
                closed = false
                state.value = ConnectionState.Connected("fake")
                connectCompleted.complete(Unit)
            }
            return Result.success(Unit)
        }

        override suspend fun disconnect() {
            disconnects.incrementAndGet()
            closed = true
            state.value = ConnectionState.Disconnected
        }

        override suspend fun send(command: String) = Unit
        override suspend fun receive(timeoutMs: Long) = ">"
        override suspend fun exchange(command: String, timeoutMs: Long): String {
            exchangeCount++
            return ">"
        }
        override suspend fun targetedExchange(requestHeader: String, request: String, timeoutMs: Long) = ">"
        override fun observeConnectionState(): Flow<ConnectionState> = state
    }
}
