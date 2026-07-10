package com.revscope.core.obd.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.R
import com.revscope.core.obd.legal.PicoYPlacaEngine
import com.revscope.core.obd.model.ObdReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_TEMP_MAX_C = 105
private const val DEFAULT_VOLTAGE_MIN = 11.8f
private const val DEFAULT_REDLINE_RPM = 10_500
private const val ALERT_COOLDOWN_MS = 30_000L
private const val REDLINE_COOLDOWN_MS = 10_000L
private const val TONE_VOLUME = 90
private const val ANOMALY_COOLDOWN_MS = 60_000L
private const val CUSTOM_ALERT_COOLDOWN_MS = 120_000L
private const val LOCAL_INFO_CHANNEL_ID = "revscope_local_info"
private const val LOCAL_INFO_NOTIFICATION_ID = 2001

/**
 * Turns telemetry readings into audible/haptic alerts. Audio goes out on the media
 * stream, so on a bike it reaches the helmet intercom over Bluetooth.
 *
 * Thresholds live in DataStore (editable in Settings) and are cached here;
 * [reloadThresholds] refreshes the cache on each new connection and after saving.
 */
@Singleton
class AlertsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: DataStore<Preferences>,
) {

    enum class AlertType { OVERHEAT, LOW_VOLTAGE, REDLINE, SPEED_CAMERA, ANOMALY, MIL_ON, CUSTOM, PICO_Y_PLACA, LOCAL_INFO }

    data class ObdAlert(
        val type: AlertType,
        val message: String,
        val value: Double,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val _alerts = MutableSharedFlow<ObdAlert>(extraBufferCapacity = 8)
    val alerts: SharedFlow<ObdAlert> = _alerts.asSharedFlow()

    @Volatile private var enabled = true
    @Volatile private var tempMaxC = DEFAULT_TEMP_MAX_C
    @Volatile private var voltageMin = DEFAULT_VOLTAGE_MIN
    @Volatile private var redlineRpm = DEFAULT_REDLINE_RPM

    /** Active vehicle profile's redline — takes precedence over the global setting. */
    @Volatile private var redlineOverride: Int? = null

    @Volatile private var ttsEnabled = true
    @Volatile private var ttsReady = false

    @Volatile private var customRules: List<CustomAlertRules.Rule> = emptyList()
    @Volatile private var milAnnouncedThisSession = false

    // ── Voice-alert categories — gate only the spoken output, not tone/vibration/banner ──
    @Volatile private var voiceTemperature = true
    @Volatile private var voiceVoltage = true
    @Volatile private var voiceSpeedCameras = true
    @Volatile private var voiceAnomalies = false
    @Volatile private var voiceMil = false
    @Volatile private var voiceRedline = false
    @Volatile private var voiceCustomThresholds = true
    @Volatile private var voiceSport = true
    @Volatile private var voicePicoPlaca = true
    @Volatile private var voiceLocalInfo = false

    private val tts: TextToSpeech by lazy {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts.language = Locale("es")
            Timber.i("AlertsEngine: TTS ready=$ttsReady")
        }
    }

    private val lastFired = mutableMapOf<AlertType, Long>()
    private val lastAnomalyAnnounced = mutableMapOf<String, Long>()
    private val lastCustomAlertFired = mutableMapOf<String, Long>()

    val currentRedlineRpm: Int get() = redlineOverride ?: redlineRpm

    fun setRedlineOverride(rpm: Int?) {
        redlineOverride = rpm
        Timber.i("AlertsEngine: redline override = $rpm")
    }

    suspend fun reloadThresholds() {
        runCatching {
            val prefs = settings.data.first()
            enabled = prefs[PreferencesKeys.ALERTS_ENABLED] ?: true
            tempMaxC = prefs[PreferencesKeys.ALERT_TEMP_MAX_C] ?: DEFAULT_TEMP_MAX_C
            voltageMin = prefs[PreferencesKeys.ALERT_VOLTAGE_MIN] ?: DEFAULT_VOLTAGE_MIN
            redlineRpm = prefs[PreferencesKeys.ALERT_REDLINE_RPM] ?: DEFAULT_REDLINE_RPM
            ttsEnabled = prefs[PreferencesKeys.ALERT_TTS_ENABLED] ?: true
            customRules = CustomAlertRules.parse(prefs[PreferencesKeys.CUSTOM_ALERTS_JSON].orEmpty())
            voiceTemperature = prefs[PreferencesKeys.VOICE_TEMPERATURE] ?: true
            voiceVoltage = prefs[PreferencesKeys.VOICE_VOLTAGE] ?: true
            voiceSpeedCameras = prefs[PreferencesKeys.VOICE_SPEED_CAMERAS] ?: true
            voiceAnomalies = prefs[PreferencesKeys.VOICE_ANOMALIES] ?: false
            voiceMil = prefs[PreferencesKeys.VOICE_MIL] ?: false
            voiceRedline = prefs[PreferencesKeys.VOICE_REDLINE] ?: false
            voiceCustomThresholds = prefs[PreferencesKeys.VOICE_CUSTOM_THRESHOLDS] ?: true
            voiceSport = prefs[PreferencesKeys.VOICE_SPORT] ?: true
            voicePicoPlaca = prefs[PreferencesKeys.VOICE_PICO_PLACA] ?: true
            voiceLocalInfo = prefs[PreferencesKeys.VOICE_LOCAL_INFO] ?: false
            if (ttsEnabled) tts // touch the lazy so the engine warms up early
            Timber.i(
                "AlertsEngine: enabled=$enabled temp=$tempMaxC volt=$voltageMin redline=$redlineRpm " +
                    "customRules=${customRules.size} voice[temp=$voiceTemperature volt=$voiceVoltage " +
                    "cam=$voiceSpeedCameras anom=$voiceAnomalies mil=$voiceMil redline=$voiceRedline " +
                    "custom=$voiceCustomThresholds sport=$voiceSport picoPlaca=$voicePicoPlaca " +
                    "localInfo=$voiceLocalInfo]"
            )
        }.onFailure { Timber.w(it, "AlertsEngine: failed to load thresholds") }
    }

    fun process(reading: ObdReading) {
        if (!enabled) return
        when (reading.pid) {
            "05" -> {
                if (!voiceTemperature) return
                if (reading.value >= tempMaxC) {
                    fire(
                        AlertType.OVERHEAT,
                        "Temperatura de motor ${reading.value.toInt()}°C",
                        reading.value,
                        tonePattern = ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK,
                        toneDurationMs = 800,
                        vibrationMs = longArrayOf(0, 400, 150, 400),
                        cooldownMs = ALERT_COOLDOWN_MS,
                        voiceEnabled = true,
                    )
                }
            }
            "0C" -> {
                if (!voiceRedline) return
                if (reading.value >= currentRedlineRpm) {
                    fire(
                        AlertType.REDLINE,
                        "RPM en zona roja: ${reading.value.toInt()}",
                        reading.value,
                        tonePattern = ToneGenerator.TONE_PROP_BEEP2,
                        toneDurationMs = 250,
                        vibrationMs = longArrayOf(0, 150),
                        cooldownMs = REDLINE_COOLDOWN_MS,
                        voiceEnabled = true,
                    )
                }
            }
            "VBAT" -> {
                if (!voiceVoltage) return
                if (reading.value > 0 && reading.value < voltageMin) {
                    fire(
                        AlertType.LOW_VOLTAGE,
                        "Batería baja: %.1fV".format(reading.value),
                        reading.value,
                        tonePattern = ToneGenerator.TONE_SUP_ERROR,
                        toneDurationMs = 600,
                        vibrationMs = longArrayOf(0, 300, 200, 300),
                        cooldownMs = ALERT_COOLDOWN_MS,
                        voiceEnabled = true,
                    )
                }
            }
        }
        evaluateCustomAlert(reading)
    }

    /** User-defined per-PID threshold from Settings' custom-alerts JSON, 120s cooldown per PID. */
    private fun evaluateCustomAlert(reading: ObdReading) {
        if (!voiceCustomThresholds) return
        if (customRules.isEmpty()) return
        val message = CustomAlertRules.evaluate(reading, customRules) ?: return
        val now = System.currentTimeMillis()
        synchronized(lastCustomAlertFired) {
            if (now - (lastCustomAlertFired[reading.pid] ?: 0L) < CUSTOM_ALERT_COOLDOWN_MS) return
            lastCustomAlertFired[reading.pid] = now
        }
        Timber.w("AlertsEngine: custom alert — $message")
        _alerts.tryEmit(ObdAlert(AlertType.CUSTOM, message, reading.value))
        playTone(ToneGenerator.TONE_SUP_ERROR, 500)
        vibrate(longArrayOf(0, 250, 150, 250))
        speak(message)
    }

    private fun fire(
        type: AlertType,
        message: String,
        value: Double,
        tonePattern: Int,
        toneDurationMs: Int,
        vibrationMs: LongArray,
        cooldownMs: Long,
        voiceEnabled: Boolean,
    ) {
        val now = System.currentTimeMillis()
        synchronized(lastFired) {
            if (now - (lastFired[type] ?: 0L) < cooldownMs) return
            lastFired[type] = now
        }
        Timber.w("AlertsEngine: $type — $message")
        _alerts.tryEmit(ObdAlert(type, message, value))
        playTone(tonePattern, toneDurationMs)
        vibrate(vibrationMs)
        if (voiceEnabled) speak(message)
    }

    /** Spoken speed-camera proximity warning. Per-camera cooldown lives in the alerter. */
    fun announceSpeedCamera(distanceM: Int, limitKmh: Int?) {
        if (!enabled) return
        if (!voiceSpeedCameras) return
        val rounded = (distanceM / 50) * 50
        val message = buildString {
            append("Radar a $rounded metros")
            limitKmh?.let { append(", límite $it") }
        }
        Timber.i("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.SPEED_CAMERA, message, distanceM.toDouble()))
        playTone(ToneGenerator.TONE_PROP_ACK, 300)
        vibrate(longArrayOf(0, 200, 100, 200))
        speak(message)
    }

    /**
     * Spoken pico-y-placa warning when GPS detects a city different from the active profile's
     * with a restriction currently active for its plate. Per-city per-day cooldown lives in
     * CityAlertPolicy/CityEnforcementAlerter — this only gates on the voice category + master switch.
     */
    fun announcePicoPlaca(cityName: String, status: PicoYPlacaEngine.Status, startHour: Int, endHour: Int) {
        if (!enabled) return
        if (!voicePicoPlaca) return
        val message = when (status) {
            PicoYPlacaEngine.Status.RESTRINGIDO_AHORA ->
                "Atención: entraste a $cityName y hoy hay pico y placa para tu placa, hasta las $endHour:00"
            PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO ->
                "Atención: en $cityName hoy aplica pico y placa para tu placa de $startHour:00 a $endHour:00"
            else -> return
        }
        Timber.w("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.PICO_Y_PLACA, message, 0.0))
        playTone(ToneGenerator.TONE_PROP_ACK, 300)
        vibrate(longArrayOf(0, 200, 100, 200))
        speak(message)
    }

    /**
     * Spoken AI-generated local info (festival, road closure…) on entering a new
     * municipality — opt-in, off by default. Per-municipio-per-day cooldown lives in
     * LocalInfoAlertPolicy/CityInfoAlerter; this only gates on the voice category +
     * master switch. Also posts a silent notification with the text so it can be reread.
     */
    fun announceLocalInfo(municipio: String, frase: String) {
        if (!enabled) return
        if (!voiceLocalInfo) return
        val message = "Estás en $municipio. $frase"
        Timber.i("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.LOCAL_INFO, message, 0.0))
        speak(message)
        postLocalInfoNotification(municipio, frase)
    }

    private fun postLocalInfoNotification(municipio: String, frase: String) {
        runCatching {
            createLocalInfoChannel()
            val notification = NotificationCompat.Builder(context, LOCAL_INFO_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_revscope)
                .setContentTitle("Estás en $municipio")
                .setContentText(frase)
                .setStyle(NotificationCompat.BigTextStyle().bigText(frase))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(LOCAL_INFO_NOTIFICATION_ID, notification)
        }.onFailure { Timber.w(it, "AlertsEngine: local info notification failed") }
    }

    private fun createLocalInfoChannel() {
        val channel = NotificationChannel(
            LOCAL_INFO_CHANNEL_ID,
            "Información local",
            NotificationManager.IMPORTANCE_LOW, // silent — TTS already spoke it
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    /** Spoken statistical-anomaly alert from AnomalyDetector — cooldown per stable alert [key]. */
    fun announceAnomaly(key: String, mensaje: String) {
        if (!enabled) return
        if (!voiceAnomalies) return
        val now = System.currentTimeMillis()
        synchronized(lastAnomalyAnnounced) {
            if (now - (lastAnomalyAnnounced[key] ?: 0L) < ANOMALY_COOLDOWN_MS) return
            lastAnomalyAnnounced[key] = now
        }
        Timber.w("AlertsEngine: anomaly — $mensaje")
        _alerts.tryEmit(ObdAlert(AlertType.ANOMALY, mensaje, 0.0))
        playTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        vibrate(longArrayOf(0, 200))
        speak(mensaje)
    }

    /** Spoken check-engine-light warning — fires once per session, until [resetSessionFlags]. */
    fun announceMilOn() {
        if (!enabled) return
        if (!voiceMil) return
        if (milAnnouncedThisSession) return
        milAnnouncedThisSession = true
        val message = "Se encendió el testigo del motor. Revisa los códigos de falla."
        Timber.w("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.MIL_ON, message, 1.0))
        playTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800)
        vibrate(longArrayOf(0, 400, 150, 400))
        speak(message)
    }

    /** Resets per-session flags (e.g. MIL-on). Call where a new telemetry session starts. */
    fun resetSessionFlags() {
        milAnnouncedThisSession = false
    }

    /** Spoken lap time — "Vuelta 3: 1 minuto 42.5" over the helmet intercom. */
    fun announceLap(lapNumber: Int, timeMs: Long) {
        if (!voiceSport) return
        val minutes = timeMs / 60_000
        val seconds = (timeMs % 60_000) / 1000.0
        val phrase = if (minutes > 0) {
            "Vuelta $lapNumber: $minutes ${if (minutes == 1L) "minuto" else "minutos"} %.1f".format(Locale("es"), seconds)
        } else {
            "Vuelta $lapNumber: %.1f segundos".format(Locale("es"), seconds)
        }
        Timber.i("AlertsEngine: $phrase")
        speak(phrase)
    }

    /** Spoken 0-100 result — reaches the helmet intercom over the media stream. */
    fun announceLaunch(to60Ms: Long?, to100Ms: Long?) {
        if (!voiceSport) return
        val phrase = when {
            to100Ms != null -> "Cero a cien en %.1f segundos".format(Locale("es"), to100Ms / 1000.0)
            to60Ms != null -> "Cero a sesenta en %.1f segundos".format(Locale("es"), to60Ms / 1000.0)
            else -> return
        }
        Timber.i("AlertsEngine: $phrase")
        speak(phrase)
    }

    private fun speak(text: String) {
        if (!enabled || !ttsEnabled) return
        runCatching {
            if (ttsReady) {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, "revscope_alert")
            }
        }.onFailure { Timber.w(it, "AlertsEngine: TTS failed") }
    }

    private fun playTone(tone: Int, durationMs: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
            generator.startTone(tone, durationMs)
            // ToneGenerator leaks the audio session if not released after playback
            android.os.Handler(context.mainLooper).postDelayed(
                { runCatching { generator.release() } },
                durationMs + 100L,
            )
        }.onFailure { Timber.w(it, "AlertsEngine: tone failed") }
    }

    private fun vibrate(pattern: LongArray) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }.onFailure { Timber.w(it, "AlertsEngine: vibration failed") }
    }
}
