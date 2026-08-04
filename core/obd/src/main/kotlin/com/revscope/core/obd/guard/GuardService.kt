package com.revscope.core.obd.guard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.R
import com.revscope.core.obd.connection.AdapterType
import com.revscope.core.obd.connection.ClassicBtTransport
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val GUARD_CHANNEL_ID = "revscope_guardia"
private const val GUARD_NOTIFICATION_ID = 5001
private const val ALARM_NOTIFICATION_ID = 5002
private const val CLASSIC_PROBE_INTERVAL_MS = 30_000L

/**
 * Modo guardia antirrobo. Diseño INVERTIDO a un tether clásico: se arma con el
 * vehículo APAGADO — la mayoría de dongles OBD se apagan (o duermen) con la
 * ignición, así que si el dongle DESPIERTA es porque alguien giró la llave:
 * el teléfono lo detecta y dispara la alarma.
 *
 * - BLE: escaneo pasivo low-power filtrado por MAC — ver el advertisement = ignición.
 * - Classic BT: sondeo RFCOMM cada 30 s — conexión exitosa = dongle con corriente.
 *
 * Limitación honesta: requiere el teléfono en rango Bluetooth (~10-30 m). Cubre el
 * robo exprés con la moto parqueada cerca (café, casa), no vigilancia remota.
 */
@AndroidEntryPoint
class GuardService : Service() {

    @Inject lateinit var settings: DataStore<Preferences>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var alarmPlayer: MediaPlayer? = null
    private var bleScanCallback: ScanCallback? = null
    @Volatile private var triggered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
        _running.value = true
        scope.launch { watchAdapter() }
        Timber.i("GuardService: armado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISARM) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopBleScan()
        stopAlarmSound()
        runCatching { notificationManager().cancel(ALARM_NOTIFICATION_ID) }
        scope.cancel()
        _running.value = false
        _alarmActive.value = false
        Timber.i("GuardService: desarmado")
        super.onDestroy()
    }

    private suspend fun watchAdapter() {
        val prefs = runCatching { settings.data.first() }.getOrNull() ?: run { stopSelf(); return }
        val address = prefs[PreferencesKeys.ADAPTER_ADDRESS] ?: run {
            Timber.w("GuardService: sin adaptador guardado — nada que vigilar")
            stopSelf()
            return
        }
        when (AdapterType.from(prefs[PreferencesKeys.ADAPTER_TYPE])) {
            AdapterType.BLE -> startBleWatch(address)
            AdapterType.CLASSIC_BT -> classicProbeLoop(address)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleWatch(address: String) {
        val scanner = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner ?: run {
            stopSelf()
            return
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trigger()
            }
        }
        runCatching {
            scanner.startScan(
                listOf(ScanFilter.Builder().setDeviceAddress(address).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
                callback,
            )
            bleScanCallback = callback
        }.onFailure {
            Timber.w(it, "GuardService: BLE scan failed")
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bleScanCallback?.let { cb ->
            runCatching { BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(cb) }
        }
        bleScanCallback = null
    }

    private suspend fun classicProbeLoop(address: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run { stopSelf(); return }
        while (!triggered) {
            val probe = ClassicBtTransport(adapter, address)
            val connected = probe.connect().isSuccess && probe.isConnected
            runCatching { probe.disconnect() }
            if (connected) {
                trigger()
                return
            }
            delay(CLASSIC_PROBE_INTERVAL_MS)
        }
    }

    private fun trigger() {
        if (triggered) return
        triggered = true
        _alarmActive.value = true
        Timber.w("GuardService: ¡dongle despertó — posible encendido del vehículo!")
        stopBleScan()
        startAlarmSound()
        postAlarmNotification()
    }

    /** Tono de alarma en el stream ALARM a volumen del sistema — debe despertar al dueño. */
    private fun startAlarmSound() {
        runCatching {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            player.isLooping = true
            player.setOnPreparedListener { mp -> if (mp === alarmPlayer) runCatching { mp.start() } }
            player.prepareAsync()
            alarmPlayer = player
        }.onFailure { Timber.w(it, "GuardService: no pude sonar la alarma") }
    }

    private fun stopAlarmSound() {
        runCatching {
            alarmPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        }
        alarmPlayer = null
    }

    private fun postAlarmNotification() {
        val notification = NotificationCompat.Builder(this, GUARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle("⚠ ¡Tu vehículo se encendió!")
            .setContentText("El adaptador OBD despertó con el modo guardia activo")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(0, "DESARMAR", disarmPendingIntent())
            .build()
        runCatching { notificationManager().notify(ALARM_NOTIFICATION_ID, notification) }
    }

    private fun startInForeground() {
        val notification: Notification = NotificationCompat.Builder(this, GUARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle("Modo guardia activo")
            .setContentText("Vigilando el adaptador OBD — si despierta, suena la alarma")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Desarmar", disarmPendingIntent())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(GUARD_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(GUARD_NOTIFICATION_ID, notification)
        }
    }

    private fun disarmPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this, 0,
            Intent(this, GuardService::class.java).setAction(ACTION_DISARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            GUARD_CHANNEL_ID, "Modo guardia antirrobo", NotificationManager.IMPORTANCE_HIGH,
        ).apply { setBypassDnd(true) }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val ACTION_DISARM = "com.revscope.core.obd.action.GUARD_DISARM"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _alarmActive = MutableStateFlow(false)
        val alarmActive: StateFlow<Boolean> = _alarmActive.asStateFlow()

        fun arm(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, GuardService::class.java))
            }.onFailure { Timber.w(it, "GuardService: no se pudo armar") }
        }

        fun disarm(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }
}
