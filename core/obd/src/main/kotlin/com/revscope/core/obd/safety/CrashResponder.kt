package com.revscope.core.obd.safety

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.R
import com.revscope.core.obd.motion.MotionMetricsHub
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val ALARM_CHANNEL_ID = "revscope_emergencia"
private const val ALARM_NOTIFICATION_ID = 4001
private const val COUNTDOWN_TOTAL_MS = 60_000L
private const val COUNTDOWN_TICK_MS = 5_000L
private const val SPEED_PID = "0D"

/**
 * Reacts to [CrashDetector] escalating to TRIGGERED: posts a full-screen alarm
 * notification with a 60s countdown to "ESTOY BIEN" and, if it expires unanswered,
 * sends an emergency SMS with the last known location. Started/stopped alongside
 * the telemetry session by [com.revscope.core.obd.service.ObdForegroundService].
 *
 * SAFETY-CRITICAL: default OFF, only monitors when CRASH_DETECTION_ENABLED is true.
 */
@Singleton
class CrashResponder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: DataStore<Preferences>,
    private val routeHolder: LiveRouteHolder,
) {

    data class AlarmState(val remainingMs: Long, val vehicleName: String)

    private val detector = CrashDetector()

    // Own long-lived scope for work that must outlive any single caller's lifecycle
    // (e.g. Settings' "Probar" button must keep counting down after the user navigates away).
    private val responderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var monitorJob: Job? = null
    @Volatile private var countdownJob: Job? = null
    @Volatile private var enabled = false
    @Volatile private var emergencyPhone = ""
    @Volatile private var vehicleName = "tu vehículo"
    @Volatile private var alarmPlayer: MediaPlayer? = null

    private val _alarmState = MutableStateFlow<AlarmState?>(null)
    val alarmState: StateFlow<AlarmState?> = _alarmState.asStateFlow()

    /** Begins feeding the detector from live IMU + speed while a telemetry session is active. */
    fun start(
        scope: CoroutineScope,
        sessionManager: ObdSessionManager,
        motionHub: MotionMetricsHub,
        vehicleName: String,
    ) {
        monitorJob?.cancel()
        this.vehicleName = vehicleName
        detector.reset()
        monitorJob = scope.launch {
            // El toggle de Settings se observa reactivo: collectLatest cancela el combine
            // cuando se apaga (cero trabajo con la detección deshabilitada) y lo relanza al
            // encenderlo mid-session sin reconectar.
            settings.data
                .map { prefs ->
                    (prefs[PreferencesKeys.CRASH_DETECTION_ENABLED] ?: false) to
                        prefs[PreferencesKeys.EMERGENCY_PHONE].orEmpty()
                }
                .distinctUntilChanged()
                .collectLatest { (isEnabled, phone) ->
                    enabled = isEnabled
                    emergencyPhone = phone
                    if (!isEnabled) return@collectLatest
                    combine(motionHub.snapshot, sessionManager.readings, routeHolder.lastSpeedKmh) { motion, readings, gpsSpeed ->
                        val obdSpeed = readings[SPEED_PID]?.value ?: 0.0
                        // Raw, pre-filter, 3-axis peak — the EMA-filtered horizontal signal used for
                        // the UI can flatten a genuine impact spike below the trigger threshold.
                        maxOf(obdSpeed, gpsSpeed.toDouble()) to motion.rawPeakG.toDouble()
                    }.collect { (speedKmh, accelG) ->
                        val state = detector.process(accelG, speedKmh, System.currentTimeMillis())
                        if (state == CrashDetector.State.TRIGGERED) handleTriggered(scope)
                    }
                }
        }
    }

    /** Stops feeding the detector — called when the session ends. Does not cancel an active alarm. */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /** Whether the Settings toggle currently allows crash monitoring. */
    fun isMonitoringEnabled(): Boolean = enabled

    /**
     * True if the detector saw real motion (> [CrashDetector.IMPACT_MIN_SPEED_KMH]) within
     * [windowMs]. Lets the foreground service decide whether a dropped OBD link deserves a
     * grace period before tearing down crash detection instead of stopping instantly.
     */
    fun hadRecentMotion(windowMs: Long): Boolean =
        detector.hadRecentMotion(windowMs, System.currentTimeMillis())

    /** "ESTOY BIEN": cancels any active countdown/alarm and rearms the detector. */
    fun cancelAlarm() {
        countdownJob?.cancel()
        countdownJob = null
        detector.reset()
        _alarmState.value = null
        stopLoopingAlarmSound()
        runCatching { notificationManager().cancel(ALARM_NOTIFICATION_ID) }
    }

    /**
     * Settings' "Probar" button — runs the full alarm/countdown UX without sending a real SMS.
     * Uses this responder's own long-lived [responderScope], not the caller's, so navigating
     * away from Settings mid-countdown doesn't cancel it and leave a frozen alarm dialog.
     */
    fun simulateTrigger(vehicleName: String) {
        this.vehicleName = vehicleName
        startAlarm(responderScope, simulated = true)
    }

    suspend fun reloadSettings() {
        runCatching {
            val prefs = settings.data.first()
            enabled = prefs[PreferencesKeys.CRASH_DETECTION_ENABLED] ?: false
            emergencyPhone = prefs[PreferencesKeys.EMERGENCY_PHONE].orEmpty()
        }.onFailure { Timber.w(it, "CrashResponder: failed to load settings") }
    }

    private fun handleTriggered(scope: CoroutineScope) {
        startAlarm(scope, simulated = false)
    }

    /** NEW-8: refuses to stomp an already-running countdown — covers both a real re-trigger
     * and [simulateTrigger] firing while a real alarm is already counting down. */
    private fun startAlarm(scope: CoroutineScope, simulated: Boolean) {
        if (countdownJob != null) return
        if (!simulated) Timber.w("CrashResponder: crash detected — starting emergency countdown")
        createAlarmChannel()
        postAlarmNotification(COUNTDOWN_TOTAL_MS)
        startLoopingAlarmSound()
        countdownJob = scope.launch {
            var remainingMs = COUNTDOWN_TOTAL_MS
            while (remainingMs > 0) {
                delay(COUNTDOWN_TICK_MS)
                remainingMs -= COUNTDOWN_TICK_MS
                postAlarmNotification(remainingMs)
            }
            onCountdownExpired(simulated)
            countdownJob = null
        }
    }

    private suspend fun onCountdownExpired(simulated: Boolean) {
        if (!simulated) sendEmergencySms()
        postFinalNotification(simulated)
        _alarmState.value = null
        detector.reset()
        stopLoopingAlarmSound()
    }

    /** Looping alarm tone on the ALARM stream — silence-after-one-shot is unacceptable here. */
    private fun startLoopingAlarmSound() {
        stopLoopingAlarmSound()
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, uri)
            player.isLooping = true
            // NEW-4: prepareAsync() callback can land after cancelAlarm()/onDestroy() already
            // released this player (e.g. "ESTOY BIEN" tapped during preparation) — starting a
            // released MediaPlayer throws. Only start if this is still the live player, and
            // never let a stray IllegalStateException escape the callback.
            player.setOnPreparedListener { mp ->
                if (mp === alarmPlayer) runCatching { mp.start() }
            }
            player.prepareAsync()
            alarmPlayer = player
        }.onFailure { Timber.w(it, "CrashResponder: failed to start alarm sound") }
    }

    private fun stopLoopingAlarmSound() {
        runCatching {
            alarmPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        }.onFailure { Timber.w(it, "CrashResponder: failed to stop alarm sound") }
        alarmPlayer = null
    }

    private suspend fun sendEmergencySms() {
        val phone = emergencyPhone
        if (phone.isBlank()) {
            Timber.w("CrashResponder: no emergency phone configured — SMS not sent")
            return
        }
        if (!hasSmsPermission()) {
            Timber.w("CrashResponder: SEND_SMS not granted — SMS not sent")
            return
        }
        val manager = smsManager()
        if (manager == null) {
            Timber.w("CrashResponder: SmsManager unavailable — SMS not sent")
            return
        }
        val message = buildEmergencyMessage()
        runCatching {
            withContext(Dispatchers.IO) {
                val parts = manager.divideMessage(message)
                manager.sendMultipartTextMessage(phone, null, parts, null, null)
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Timber.e(e, "CrashResponder: failed to send emergency SMS")
        }
    }

    private fun smsManager(): SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(SmsManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
    }

    // ASCII-only (GSM-7 safe): emoji or accented characters force UCS-2 encoding, which halves
    // the per-segment length and previously truncated the location URL out of the message.
    private fun buildEmergencyMessage(): String {
        val location = routeHolder.lastPoint.value
        val locationText = location
            ?.let { "https://maps.google.com/?q=${it.lat},${it.lon}" }
            ?: "ubicacion no disponible"
        return "RevScope: posible caida detectada ($vehicleName). Ubicacion: $locationText"
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun postAlarmNotification(remainingMs: Long) {
        _alarmState.value = AlarmState(remainingMs, vehicleName)
        runCatching { notificationManager().notify(ALARM_NOTIFICATION_ID, buildAlarmNotification(remainingMs)) }
    }

    private fun buildAlarmNotification(remainingMs: Long): Notification {
        val seconds = remainingMs / 1000
        val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle("Posible caída detectada")
            .setContentText("Si no respondes, se enviará un SMS de emergencia en ${seconds}s")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "ESTOY BIEN", cancelPendingIntent())
        fullScreenPendingIntent()?.let { builder.setFullScreenIntent(it, true) }
        return builder.build()
    }

    private fun postFinalNotification(simulated: Boolean) {
        val title = if (simulated) "Prueba completada" else "SMS de emergencia enviado"
        val text = if (simulated) {
            "Simulación sin envío real de SMS"
        } else {
            "Se avisó a tu contacto de emergencia con tu última ubicación"
        }
        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { notificationManager().notify(ALARM_NOTIFICATION_ID, notification) }
    }

    private fun fullScreenPendingIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_CRASH_ALERT, true)
            ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, ALARM_NOTIFICATION_ID, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelPendingIntent(): PendingIntent {
        val intent = Intent(context, CrashCancelReceiver::class.java).setAction(ACTION_CANCEL)
        return PendingIntent.getBroadcast(
            context, ALARM_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createAlarmChannel() {
        val channel = NotificationChannel(
            ALARM_CHANNEL_ID, "Emergencia — detección de caída", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(alarmUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val EXTRA_CRASH_ALERT = "crash_alert"
        const val ACTION_CANCEL = "com.revscope.action.CRASH_CANCEL"
    }
}
