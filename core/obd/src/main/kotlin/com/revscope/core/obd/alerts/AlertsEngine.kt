package com.revscope.core.obd.alerts

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
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

    enum class AlertType { OVERHEAT, LOW_VOLTAGE, REDLINE }

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

    private val lastFired = mutableMapOf<AlertType, Long>()

    val currentRedlineRpm: Int get() = redlineRpm

    suspend fun reloadThresholds() {
        runCatching {
            val prefs = settings.data.first()
            enabled = prefs[PreferencesKeys.ALERTS_ENABLED] ?: true
            tempMaxC = prefs[PreferencesKeys.ALERT_TEMP_MAX_C] ?: DEFAULT_TEMP_MAX_C
            voltageMin = prefs[PreferencesKeys.ALERT_VOLTAGE_MIN] ?: DEFAULT_VOLTAGE_MIN
            redlineRpm = prefs[PreferencesKeys.ALERT_REDLINE_RPM] ?: DEFAULT_REDLINE_RPM
            Timber.i("AlertsEngine: enabled=$enabled temp=$tempMaxC volt=$voltageMin redline=$redlineRpm")
        }.onFailure { Timber.w(it, "AlertsEngine: failed to load thresholds") }
    }

    fun process(reading: ObdReading) {
        if (!enabled) return
        when (reading.pid) {
            "05" -> if (reading.value >= tempMaxC) {
                fire(
                    AlertType.OVERHEAT,
                    "Temperatura de motor ${reading.value.toInt()}°C",
                    reading.value,
                    tonePattern = ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK,
                    toneDurationMs = 800,
                    vibrationMs = longArrayOf(0, 400, 150, 400),
                    cooldownMs = ALERT_COOLDOWN_MS,
                )
            }
            "0C" -> if (reading.value >= redlineRpm) {
                fire(
                    AlertType.REDLINE,
                    "RPM en zona roja: ${reading.value.toInt()}",
                    reading.value,
                    tonePattern = ToneGenerator.TONE_PROP_BEEP2,
                    toneDurationMs = 250,
                    vibrationMs = longArrayOf(0, 150),
                    cooldownMs = REDLINE_COOLDOWN_MS,
                )
            }
            "VBAT" -> if (reading.value > 0 && reading.value < voltageMin) {
                fire(
                    AlertType.LOW_VOLTAGE,
                    "Batería baja: %.1fV".format(reading.value),
                    reading.value,
                    tonePattern = ToneGenerator.TONE_SUP_ERROR,
                    toneDurationMs = 600,
                    vibrationMs = longArrayOf(0, 300, 200, 300),
                    cooldownMs = ALERT_COOLDOWN_MS,
                )
            }
        }
    }

    private fun fire(
        type: AlertType,
        message: String,
        value: Double,
        tonePattern: Int,
        toneDurationMs: Int,
        vibrationMs: LongArray,
        cooldownMs: Long,
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
