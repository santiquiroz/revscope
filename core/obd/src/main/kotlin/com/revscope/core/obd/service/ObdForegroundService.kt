package com.revscope.core.obd.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.obd.R
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.motion.MotionMetricsHub
import com.revscope.core.obd.motion.MotionSensorRecorder
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.track.TrackModeEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val CHANNEL_ID = "revscope_telemetry"
private const val NOTIFICATION_ID = 1001
private const val NOTIFICATION_UPDATE_MS = 5_000L

/**
 * Keeps telemetry recording and the GPS track alive when the app is backgrounded
 * or the screen is off. Started by [ObdSessionManager] on connection, stopped on
 * explicit disconnect. The notification doubles as a live mini-dashboard.
 */
@AndroidEntryPoint
class ObdForegroundService : Service() {

    @Inject lateinit var sessionManager: ObdSessionManager
    @Inject lateinit var gpsDao: GpsDao
    @Inject lateinit var imuDao: ImuDao
    @Inject lateinit var trackModeEngine: TrackModeEngine
    @Inject lateinit var cameraAlerter: SpeedCameraAlerter
    @Inject lateinit var motionHub: MotionMetricsHub
    @Inject lateinit var routeHolder: LiveRouteHolder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var gpsRecorder: GpsTrackRecorder? = null
    private var motionRecorder: MotionSensorRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
        observeSession()
        Timber.i("ObdForegroundService: started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        gpsRecorder?.stop()
        motionRecorder?.stop()
        scope.cancel()
        Timber.i("ObdForegroundService: destroyed")
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildNotification("Conectando…", null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (hasLocationPermission()) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeSession() {
        // GPS track follows the recording session lifecycle
        scope.launch {
            sessionManager.currentSessionId.collect { sessionId ->
                gpsRecorder?.stop()
                gpsRecorder = null
                motionRecorder?.stop()
                motionRecorder = null
                routeHolder.clear()
                if (sessionId != null) {
                    val imu = MotionSensorRecorder(applicationContext, imuDao, motionHub).also {
                        it.start(scope, sessionId)
                    }
                    motionRecorder = imu
                    gpsRecorder = GpsTrackRecorder(
                        applicationContext,
                        gpsDao,
                        trackModeEngine,
                        onBearing = imu::updateGpsBearing,
                        cameraAlerter = cameraAlerter,
                        routeHolder = routeHolder,
                    ).also { it.start(scope, sessionId) }
                }
            }
        }

        // Live notification content
        scope.launch {
            while (true) {
                updateNotification()
                delay(NOTIFICATION_UPDATE_MS)
            }
        }
    }

    private fun updateNotification() {
        val state = sessionManager.connectionState.value
        val readings = sessionManager.readings.value
        val title = when (state) {
            is ConnectionState.Connected -> "Conectado a ${state.deviceName}"
            ConnectionState.Connecting -> "Conectando…"
            is ConnectionState.Error -> "Enlace perdido — reintentando"
            ConnectionState.Disconnected -> "Desconectado"
        }
        val temp = readings["05"]?.value?.toInt()
        val speed = readings["0D"]?.value?.toInt()
        val volts = readings[ObdSessionManager.VBAT_PID]?.value
        val body = buildList {
            speed?.let { add("$it km/h") }
            temp?.let { add("$it°C") }
            volts?.let { add("%.1fV".format(it)) }
        }.joinToString("  ·  ").ifEmpty { "Grabando telemetría" }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, body))
    }

    private fun buildNotification(title: String, body: String?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(launchAppIntent())
            .build()

    private fun launchAppIntent() =
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Telemetría RevScope",
            NotificationManager.IMPORTANCE_LOW, // silent — the alerts engine handles sounds
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, ObdForegroundService::class.java))
            }.onFailure { Timber.w(it, "ObdForegroundService: could not start (app in background?)") }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ObdForegroundService::class.java))
        }
    }
}
