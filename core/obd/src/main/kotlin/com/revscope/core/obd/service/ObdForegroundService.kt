package com.revscope.core.obd.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.obd.R
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.cameras.CameraCoverageTracker
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.legal.CityEnforcementAlerter
import com.revscope.core.obd.motion.MotionMetricsHub
import com.revscope.core.obd.motion.MotionSensorRecorder
import com.revscope.core.obd.safety.CrashResponder
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.track.TrackModeEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

private const val CHANNEL_ID = "revscope_telemetry"
private const val NOTIFICATION_ID = 1001
private const val NOTIFICATION_UPDATE_MS = 5_000L

// C1: a real crash severs the OBD link too (adapter jarred loose, tip-over cuts ignition),
// so losing the session is not proof the ride is over — give crash detection a grace window
// fed by GPS + IMU alone before tearing the subsystem down.
// NEW-3: 90s, not 3 min — CrashDetector's immobility window (IMMOBILITY_DURATION_MS) is only
// 30s and the impact necessarily precedes the link loss, so 90s covers TRIGGERED with margin
// at half the battery cost of the previous 180s window.
private const val CRASH_GRACE_PERIOD_MS = 90_000L
private const val CRASH_GRACE_MOTION_LOOKBACK_MS = 60_000L
private const val ALARM_DRAIN_TIMEOUT_MS = 180_000L

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
    @Inject lateinit var coverageTracker: CameraCoverageTracker
    @Inject lateinit var cityAlerter: CityEnforcementAlerter
    @Inject lateinit var localInfoSink: GpsInfoSink
    @Inject lateinit var sunsetAlerter: com.revscope.core.obd.alerts.SunsetAlerter
    @Inject lateinit var potholeTracker: com.revscope.core.obd.road.PotholeTracker
    @Inject lateinit var potholeAlerter: com.revscope.core.obd.road.PotholeAlerter
    @Inject lateinit var rainWatcher: com.revscope.core.obd.weather.RainWatcher
    @Inject lateinit var wetLeanGuard: com.revscope.core.obd.road.WetLeanGuard
    @Inject lateinit var fatigueCoach: com.revscope.core.obd.road.FatigueCoach
    @Inject lateinit var potholeSync: com.revscope.core.obd.social.PotholeSync
    @Inject lateinit var motionHub: MotionMetricsHub
    @Inject lateinit var routeHolder: LiveRouteHolder
    @Inject lateinit var navigationController: com.revscope.core.navigation.NavigationController
    @Inject lateinit var crashResponder: CrashResponder
    @Inject lateinit var engineSound: com.revscope.core.obd.sound.EngineSoundController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var gpsRecorder: GpsTrackRecorder? = null
    private var motionRecorder: MotionSensorRecorder? = null
    private var graceJob: Job? = null

    // Pantalla apagada: el scheduler OBD estira intervalos y la notificación deja de
    // redibujarse — nadie la ve y cada notify() despierta SystemUI.
    @Volatile private var screenOn = true
    private var lastNotificationKey: Pair<String, String?>? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val interactive = intent.action != Intent.ACTION_SCREEN_OFF
            screenOn = interactive
            sessionManager.setIdleMode(!interactive)
            if (interactive) updateNotification(force = true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        screenOn = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false
        sessionManager.setIdleMode(!screenOn)
        observeSession()
        Timber.i("ObdForegroundService: started")
    }

    // NOT_STICKY: el estado de sesión vive en el proceso — si el sistema mata el proceso,
    // el restart sticky revivía un servicio zombie sin sesión que nunca llamaba stopSelf().
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHUTDOWN) handleShutdownRequest()
        return START_NOT_STICKY
    }

    /**
     * Graceful stop request from [ObdSessionManager.finalShutdown]. Unlike [stop], this defers
     * to an in-progress crash-detection grace period instead of killing it — the grace job
     * itself calls [stopSelf] once it's done (see [runCrashGrace]).
     */
    private fun handleShutdownRequest() {
        if (graceJob?.isActive == true) {
            Timber.i("ObdForegroundService: shutdown requested — crash-detection grace in progress, deferring")
            return
        }
        Timber.i("ObdForegroundService: shutdown requested — stopping")
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        sessionManager.setIdleMode(false)
        graceJob?.cancel()
        gpsRecorder?.stop()
        motionRecorder?.stop()
        engineSound.stop()
        // NEW-2: if the service dies mid-real-countdown (e.g. manual disconnect while an
        // alarm is counting down), cancel it here too — otherwise the looping alarm sound
        // and notification outlive the service with nothing left to stop them.
        crashResponder.cancelAlarm()
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
                graceJob?.cancel()
                graceJob = null
                if (sessionId != null) startSession(sessionId) else handleSessionLost()
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

    private fun startSession(sessionId: Long) {
        gpsRecorder?.stop()
        motionRecorder?.stop()
        routeHolder.clear()
        val imu = MotionSensorRecorder(applicationContext, imuDao, motionHub).also {
            it.onVerticalSpike = potholeTracker::onVerticalSpike
            it.start(scope, sessionId)
        }
        motionRecorder = imu
        wetLeanGuard.start(scope)
        fatigueCoach.onSessionStart()
        gpsRecorder = GpsTrackRecorder(
            applicationContext,
            gpsDao,
            trackModeEngine,
            onBearing = imu::updateGpsBearing,
            cameraAlerter = cameraAlerter,
            coverageTracker = coverageTracker,
            cityAlerter = cityAlerter,
            localInfoSink = localInfoSink,
            routeHolder = routeHolder,
            sunsetAlerter = sunsetAlerter,
            potholeAlerter = potholeAlerter,
            rainWatcher = rainWatcher,
            potholeSync = potholeSync,
            onFixTick = { fatigueCoach.onTick(sessionManager.readings.value[com.revscope.core.obd.road.FatigueCoach.PID]?.value) },
            // Wired unconditionally — the OBD dashboard's speed-source toggle and the
            // speedometer comparison screen need GPS_SPEED too, not just GPS-only trips.
            // publishGpsSpeed() itself decides whether to also feed engineOffDetector.
            onSpeed = sessionManager::publishGpsSpeed,
            navigation = navigationController,
        ).also { it.start(scope, sessionId) }
        crashResponder.start(
            scope = scope,
            sessionManager = sessionManager,
            motionHub = motionHub,
            vehicleName = sessionManager.activeProfile.value?.name ?: "tu vehículo",
        )
        engineSound.start()
    }

    /**
     * C1: the session ID goes null both for a benign disconnect AND for the exact moment a
     * real crash severs the OBD link (adapter jarred loose, tip-over cuts ignition) — so this
     * cannot tear the crash subsystem down unconditionally. If detection is enabled and there
     * was real motion recently, keep GPS + IMU + the responder alive on the old session for a
     * grace window instead: GpsTrackRecorder and MotionSensorRecorder don't depend on the OBD
     * link at all, and CrashResponder already falls back to GPS speed
     * ([LiveRouteHolder.lastSpeedKmh]) once OBD readings go stale/empty, so simply not tearing
     * the existing recorders down is enough to keep monitoring alive — no separate location
     * listener needed.
     */
    private fun handleSessionLost() {
        // Sin link OBD el mapa de lecturas congela la última RPM — el sonido seguiría
        // rugiendo con el motor apagado; se corta siempre, incluso durante la gracia.
        engineSound.stop()
        if (shouldEnterCrashGrace()) {
            Timber.i("ObdForegroundService: link lost with recent motion — entering ${CRASH_GRACE_PERIOD_MS}ms crash-detection grace")
            // NEW-1: the closed session must not keep growing — recorders stay alive to feed
            // MotionMetricsHub/LiveRouteHolder/cameraAlerter/trackModeEngine (and thus
            // CrashResponder) but stop writing rows against a sessionId that already has its
            // aggregates computed.
            motionRecorder?.setPersistenceEnabled(false)
            gpsRecorder?.setPersistenceEnabled(false)
            graceJob = scope.launch { runCrashGrace() }
        } else {
            stopCrashSubsystemAndRecorders()
        }
    }

    private fun shouldEnterCrashGrace(): Boolean =
        crashResponder.isMonitoringEnabled() && crashResponder.hadRecentMotion(CRASH_GRACE_MOTION_LOOKBACK_MS)

    private suspend fun runCrashGrace() {
        delay(CRASH_GRACE_PERIOD_MS)
        // If a crash triggered mid-grace, the alarm/SMS flow must be allowed to finish.
        // Acotado: countdown (60s) + SMS caben de sobra en 3 min — sin tope, un estado de
        // alarma que nunca se limpia dejaba GPS + IMU vivos indefinidamente.
        withTimeoutOrNull(ALARM_DRAIN_TIMEOUT_MS) {
            crashResponder.alarmState.first { it == null }
        }
        stopCrashSubsystemAndRecorders()
        stopSelf()
    }

    private fun stopCrashSubsystemAndRecorders() {
        gpsRecorder?.stop()
        gpsRecorder = null
        motionRecorder?.onVerticalSpike = null
        motionRecorder?.stop()
        motionRecorder = null
        wetLeanGuard.stop(motionHub.snapshot.value.maxAbsLean)
        fatigueCoach.onSessionEnd()
        crashResponder.stop()
        routeHolder.clear()
    }

    private fun updateNotification(force: Boolean = false) {
        // Con la pantalla apagada nadie ve la notificación; el force del screen-on la refresca.
        if (!screenOn && !force) return
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

        val key: Pair<String, String?> = title to body
        if (!force && key == lastNotificationKey) return
        lastNotificationKey = key
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
        private const val ACTION_SHUTDOWN = "com.revscope.core.obd.action.SHUTDOWN"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, ObdForegroundService::class.java))
            }.onFailure { Timber.w(it, "ObdForegroundService: could not start (app in background?)") }
        }

        /** Immediate, unconditional stop — reserved for user-initiated disconnect. */
        fun stop(context: Context) {
            context.stopService(Intent(context, ObdForegroundService::class.java))
        }

        /**
         * Graceful stop request: unlike [stop], the service itself decides whether to honor it
         * now or defer while an active crash-detection grace period is in progress.
         */
        fun requestShutdown(context: Context) {
            runCatching {
                context.startService(Intent(context, ObdForegroundService::class.java).setAction(ACTION_SHUTDOWN))
            }.onFailure { e ->
                Timber.w(e, "ObdForegroundService: could not request shutdown")
                // NEW-6: startService threw (e.g. background-start restriction) — the ACTION_SHUTDOWN
                // intent never reached the service, so fall back to an unconditional stop instead of
                // silently leaving it running forever.
                runCatching { context.stopService(Intent(context, ObdForegroundService::class.java)) }
            }
        }
    }
}
