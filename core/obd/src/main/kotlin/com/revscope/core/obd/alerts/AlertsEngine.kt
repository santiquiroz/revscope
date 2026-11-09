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
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.units.DistanceUnit
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.obd.R
import com.revscope.core.obd.legal.CityRulesFormatter
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
import kotlin.math.roundToInt

private const val DEFAULT_TEMP_MAX_C = 105
private const val DEFAULT_VOLTAGE_MIN = 11.8f
private const val CAR_DEFAULT_REDLINE_RPM = 6_500
private const val MOTORCYCLE_DEFAULT_REDLINE_RPM = 10_500
private const val ALERT_COOLDOWN_MS = 30_000L
private const val REDLINE_COOLDOWN_MS = 10_000L
private const val TONE_VOLUME = 90
private const val ANOMALY_COOLDOWN_MS = 60_000L
private const val CUSTOM_ALERT_COOLDOWN_MS = 120_000L
private const val LOCAL_INFO_CHANNEL_ID = "revscope_local_info"
private const val LOCAL_INFO_NOTIFICATION_ID = 2001
private const val AI_RULES_NOTIFICATION_ID = 2002
private const val ZONE_BRIEF_NOTIFICATION_ID = 2003
// Decimal seconds retain plural wording, including Spanish's existing "1,0 segundos". // i18n-ignore
private const val DECIMAL_SECONDS_PLURAL_COUNT = 2
private const val DECIMAL_DISTANCE_PLURAL_COUNT = 2

internal fun chooseTtsLocale(requested: Locale, availability: Int, engineDefault: Locale): Locale =
    if (availability >= TextToSpeech.LANG_AVAILABLE) requested else engineDefault

internal fun lapPhrase(resolver: StringResolver, lapNumber: Int, timeMs: Long): String {
    val minutes = (timeMs / 60_000).toInt()
    val seconds = (timeMs % 60_000) / 1000.0
    val formattedSeconds = LocaleNumbers.decimal(seconds, 1, resolver.locale)
    return if (minutes > 0) {
        resolver.plural(R.plurals.obd_lap_minutes, minutes, lapNumber, minutes, formattedSeconds)
    } else {
        resolver.plural(R.plurals.obd_lap_seconds, DECIMAL_SECONDS_PLURAL_COUNT, lapNumber, formattedSeconds)
    }
}

internal fun launchPhrase(
    resolver: StringResolver,
    to60Ms: Long?,
    to100Ms: Long?,
    to60MphMs: Long?,
    system: UnitSystem,
): String? =
    if (system == UnitSystem.METRIC) {
        metricLaunchPhrase(resolver, to60Ms, to100Ms)
    } else {
        to60MphMs?.let { secondsPhrase(resolver, R.plurals.obd_launch_zero_to_sixty_mph, it) }
    }

private fun secondsPhrase(resolver: StringResolver, resource: Int, timeMs: Long): String =
    resolver.plural(
        resource,
        DECIMAL_SECONDS_PLURAL_COUNT,
        LocaleNumbers.decimal(timeMs / 1000.0, 1, resolver.locale),
    )

private fun metricLaunchPhrase(resolver: StringResolver, to60Ms: Long?, to100Ms: Long?): String? {
    val timeMs: Long
    val resource: Int
    when {
        to100Ms != null -> {
            timeMs = to100Ms
            resource = R.plurals.obd_launch_zero_to_hundred
        }
        to60Ms != null -> {
            timeMs = to60Ms
            resource = R.plurals.obd_launch_zero_to_sixty
        }
        else -> return null
    }
    return secondsPhrase(resolver, resource, timeMs)
}

internal fun spokenSpeed(resolver: StringResolver, units: UnitFormatter, kmh: Double): String {
    val value = units.speed(kmh).roundToInt()
    val id = if (units.system == UnitSystem.METRIC) R.plurals.obd_units_spoken_kmh else R.plurals.obd_units_spoken_mph
    return resolver.plural(id, value, value)
}

internal fun spokenTemperature(resolver: StringResolver, units: UnitFormatter, celsius: Double): String {
    val value = units.temperature(celsius).toInt()
    return resolver.plural(R.plurals.obd_units_spoken_degrees, value, value)
}

internal fun spokenDistance(resolver: StringResolver, units: UnitFormatter, meters: Int): String {
    val measure = units.shortDistance(meters.toDouble())
    return when (measure.unit) {
        DistanceUnit.METERS -> resolver.plural(R.plurals.obd_units_spoken_meters, meters, meters)
        DistanceUnit.FEET -> wholePlural(resolver, R.plurals.obd_units_spoken_feet, measure.value)
        DistanceUnit.KILOMETERS -> longDistancePlural(resolver, R.plurals.obd_units_spoken_kilometers, measure.value)
        DistanceUnit.MILES -> longDistancePlural(resolver, R.plurals.obd_units_spoken_miles, measure.value)
    }
}

private fun wholePlural(resolver: StringResolver, id: Int, value: Double): String {
    val whole = value.roundToInt()
    return resolver.plural(id, whole, whole)
}

// A camera 300 m ahead is 0.2 mi: rounding to a whole mile would say "0 miles".
private fun longDistancePlural(resolver: StringResolver, id: Int, value: Double): String {
    val tenths = (value * 10).roundToInt()
    if (tenths % 10 != 0) {
        return resolver.plural(id, DECIMAL_DISTANCE_PLURAL_COUNT, LocaleNumbers.decimal(value, 1, resolver.locale))
    }
    val whole = tenths / 10
    return resolver.plural(id, whole, LocaleNumbers.decimal(whole.toDouble(), 0, resolver.locale))
}

internal fun overheatPhrase(resolver: StringResolver, units: UnitFormatter, celsius: Double): String =
    resolver.get(R.string.obd_alert_temperature, spokenTemperature(resolver, units, celsius))

internal fun speedCameraPhrase(resolver: StringResolver, units: UnitFormatter, distanceM: Int, limitKmh: Int?): String {
    val rounded = (distanceM / 50) * 50
    return buildString {
        append(resolver.get(R.string.obd_speed_camera_distance, spokenDistance(resolver, units, rounded)))
        limitKmh?.let {
            append(resolver.get(R.string.obd_speed_camera_limit, spokenSpeed(resolver, units, it.toDouble())))
        }
    }
}

internal fun potholePhrase(resolver: StringResolver, units: UnitFormatter, distanceM: Int): String =
    resolver.get(R.string.obd_pothole_reported, spokenDistance(resolver, units, distanceM))

// Un toque corto: marca el cambio de paso sin confundirse con el patrón largo de una alerta.
private val VIBRACION_PASO_TALLER = longArrayOf(0, 120)

/** Redline fallback when there's no per-profile value — moto redlines run much hotter than car. */
private fun defaultRedlineRpm(vehicleType: VehicleType): Int =
    if (vehicleType == VehicleType.MOTORCYCLE) MOTORCYCLE_DEFAULT_REDLINE_RPM else CAR_DEFAULT_REDLINE_RPM

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
    private val resolver: StringResolver,
    private val units: UnitFormatter,
) {

    enum class AlertType { OVERHEAT, LOW_VOLTAGE, REDLINE, SPEED_CAMERA, ANOMALY, MIL_ON, CUSTOM, PICO_Y_PLACA, LOCAL_INFO, SUNSET, POTHOLE, RAIN, FATIGUE, ZONE_BRIEF, CRASH_COUNTDOWN, NAVIGATION }

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

    /** Type of the last profile seen through [setRedlineOverride] — drives the redline fallback. */
    @Volatile private var activeVehicleType = VehicleType.CAR
    @Volatile private var redlineRpm = defaultRedlineRpm(activeVehicleType)

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
    @Volatile private var voiceSunset = true
    @Volatile private var voicePotholes = true
    @Volatile private var voiceRain = true
    @Volatile private var voiceFatigue = true
    @Volatile private var voiceZoneBrief = true

    private val tts: TextToSpeech by lazy {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts.language = chooseTtsLocale(
                    resolver.locale,
                    tts.isLanguageAvailable(resolver.locale),
                    tts.defaultLanguage,
                )
            }
            Timber.i("AlertsEngine: TTS ready=$ttsReady")
        }
    }

    private val lastFired = mutableMapOf<AlertType, Long>()
    private val lastAnomalyAnnounced = mutableMapOf<String, Long>()
    private val lastCustomAlertFired = mutableMapOf<String, Long>()

    val currentRedlineRpm: Int get() = redlineOverride ?: redlineRpm

    fun setRedlineOverride(rpm: Int?, vehicleType: VehicleType) {
        redlineOverride = rpm
        activeVehicleType = vehicleType
        Timber.i("AlertsEngine: redline override = $rpm ($vehicleType)")
    }

    suspend fun reloadThresholds() {
        runCatching {
            val prefs = settings.data.first()
            enabled = prefs[PreferencesKeys.ALERTS_ENABLED] ?: true
            tempMaxC = prefs[PreferencesKeys.ALERT_TEMP_MAX_C] ?: DEFAULT_TEMP_MAX_C
            voltageMin = prefs[PreferencesKeys.ALERT_VOLTAGE_MIN] ?: DEFAULT_VOLTAGE_MIN
            redlineRpm = prefs[PreferencesKeys.ALERT_REDLINE_RPM] ?: defaultRedlineRpm(activeVehicleType)
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
            voiceSunset = prefs[PreferencesKeys.VOICE_SUNSET] ?: true
            voicePotholes = prefs[PreferencesKeys.VOICE_POTHOLES] ?: true
            voiceRain = prefs[PreferencesKeys.VOICE_RAIN] ?: true
            voiceFatigue = prefs[PreferencesKeys.VOICE_FATIGUE] ?: true
            voiceZoneBrief = prefs[PreferencesKeys.VOICE_ZONE_BRIEF] ?: true
            if (ttsEnabled) tts // touch the lazy so the engine warms up early
            Timber.i(
                "AlertsEngine: enabled=$enabled temp=$tempMaxC volt=$voltageMin redline=$redlineRpm " + // i18n-ignore
                "customRules=${customRules.size} voice[temp=$voiceTemperature volt=$voiceVoltage " + // i18n-ignore
                "cam=$voiceSpeedCameras anom=$voiceAnomalies mil=$voiceMil redline=$voiceRedline " + // i18n-ignore
                "custom=$voiceCustomThresholds sport=$voiceSport picoPlaca=$voicePicoPlaca " + // i18n-ignore
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
                        overheatPhrase(resolver, units, reading.value),
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
                        resolver.get(R.string.obd_alert_redline, reading.value.toInt()),
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
                        resolver.get(
                            R.string.obd_alert_low_battery,
                            LocaleNumbers.decimal(reading.value, 1, resolver.locale),
                        ),
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
        val message = CustomAlertRules.evaluate(reading, customRules, resolver) ?: return
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

    /**
     * Cuenta regresiva hablada de la detección de caída.
     *
     * IGNORA A PROPÓSITO el interruptor maestro y las categorías de voz: mientras se rueda,
     * la voz por el stream de media (intercomunicador del casco) es el ÚNICO canal por el que
     * el conductor puede enterarse de que hay una cuenta regresiva y cancelarla. La alarma en
     * el stream de alarma queda en el parlante del celular — en el bolsillo, bajo la chaqueta
     * y con el motor andando, es inaudible. Un falso positivo silencioso manda un SMS de
     * emergencia real (pasó en campo el 2026-08-08).
     */
    fun announceCrashCountdown(secondsRemaining: Int) {
        val message = if (secondsRemaining > 0) {
            resolver.get(
                R.string.obd_crash_countdown_start,
                resolver.plural(
                    R.plurals.obd_crash_countdown_seconds,
                    secondsRemaining,
                    secondsRemaining,
                ),
            )
        } else {
            resolver.get(R.string.obd_crash_sending_message)
        }
        Timber.w("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.CRASH_COUNTDOWN, message, secondsRemaining.toDouble()))
        playTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800)
        vibrate(longArrayOf(0, 600, 200, 600, 200, 600))
        speakUnconditionally(message)
    }

    /**
     * Instrucción de navegación paso a paso.
     *
     * No consulta los interruptores de alertas porque no es una alerta: el conductor pidió
     * que lo guiaran, y callar una instrucción de giro por tener silenciados los avisos de
     * radar sería obedecer la preferencia equivocada. Sí respeta el interruptor de voz: si el
     * TTS está apagado, la navegación queda visual, que es lo que se pidió.
     *
     * Cuándo hablar lo decide ManeuverAnnouncer; aquí solo se dice.
     */
    fun announceNavigation(instruction: String) {
        if (!ttsEnabled) return
        Timber.i("AlertsEngine: navegación — $instruction")
        _alerts.tryEmit(ObdAlert(AlertType.NAVIGATION, instruction, 0.0))
        speakUnconditionally(instruction)
    }

    /**
     * Guía por voz de una prueba del Taller («Paso 2 de 5: medio…»). Como la navegación, la pidió el
     * técnico: no consulta los interruptores de alertas, pero sí el de voz. Vibra siempre: con las manos
     * en el acelerador la vibración marca el cambio de paso aunque la voz esté apagada.
     */
    fun anunciarPasoTaller(texto: String) {
        vibrate(VIBRACION_PASO_TALLER)
        if (!ttsEnabled) return
        Timber.i("AlertsEngine: taller — $texto")
        speakUnconditionally(sanitizeForSpeech(texto))
    }

    /** Spoken speed-camera proximity warning. Per-camera cooldown lives in the alerter. */
    fun announceSpeedCamera(distanceM: Int, limitKmh: Int?) {
        if (!enabled) return
        if (!voiceSpeedCameras) return
        val message = speedCameraPhrase(resolver, units, distanceM, limitKmh)
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
                resolver.get(R.string.obd_pico_placa_active, cityName, endHour)
            PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO ->
                resolver.get(R.string.obd_pico_placa_today, cityName, startHour, endHour)
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
    /** Hueco del mapa personal adelante en el cono de rumbo. */
    fun announcePothole(distanceM: Int) {
        if (!enabled) return
        if (!voicePotholes) return
        val message = potholePhrase(resolver, units, distanceM)
        Timber.i("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.POTHOLE, message, distanceM.toDouble()))
        playTone(ToneGenerator.TONE_PROP_BEEP, 150)
        vibrate(longArrayOf(0, 150))
        speak(message)
    }

    /** Lluvia inminente o empezando — los primeros minutos son los más resbalosos. */
    fun announceRain(startingNow: Boolean, minutesAhead: Int) {
        if (!enabled) return
        if (!voiceRain) return
        val message = if (startingNow) {
            resolver.get(R.string.obd_rain_starting)
        } else {
            resolver.get(R.string.obd_rain_forecast, minutesAhead)
        }
        Timber.i("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.RAIN, message, minutesAhead.toDouble()))
        playTone(ToneGenerator.TONE_PROP_ACK, 200)
        speak(message)
    }

    /** Inclinación excesiva para piso mojado (guardián wet-lean). */
    fun announceWetLean(leanDeg: Int) {
        if (!enabled) return
        if (!voiceRain) return
        val message = resolver.get(R.string.obd_wet_lean_warning, leanDeg)
        Timber.w("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.RAIN, message, leanDeg.toDouble()))
        playTone(ToneGenerator.TONE_SUP_ERROR, 250)
        vibrate(longArrayOf(0, 300))
        speak(message)
    }

    /** Coach de fatiga: pausa cada 2 h / hidratación con calor. */
    fun announceFatigue(hydration: Boolean) {
        if (!enabled) return
        if (!voiceFatigue) return
        val message = if (hydration) {
            resolver.get(R.string.obd_fatigue_hydration)
        } else {
            resolver.get(R.string.obd_fatigue_break)
        }
        Timber.i("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.FATIGUE, message, 0.0))
        speak(message)
    }

    /** Aviso único diario ~25 min antes del ocaso — pico de riesgo para motos. */
    fun announceSunset(minutesToSunset: Int) {
        if (!enabled) return
        if (!voiceSunset) return
        val message = resolver.get(R.string.obd_sunset_warning, minutesToSunset)
        Timber.i("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.SUNSET, message, minutesToSunset.toDouble()))
        playTone(ToneGenerator.TONE_PROP_ACK, 200)
        speak(message)
    }

    fun announceLocalInfo(municipio: String, frase: String) {
        if (!enabled) return
        if (!voiceLocalInfo) return
        val sanitized = sanitizeForSpeech(frase)
        if (sanitized.isEmpty()) return
        val message = resolver.get(R.string.obd_local_info_intro, municipio, sanitized)
        Timber.i("AlertsEngine: $message")
        _alerts.tryEmit(ObdAlert(AlertType.LOCAL_INFO, message, 0.0))
        speak(message)
        postLocalInfoNotification(municipio, frase)
    }

    private fun postLocalInfoNotification(municipio: String, frase: String) {
        postSilentInfoNotification(LOCAL_INFO_NOTIFICATION_ID, resolver.get(R.string.obd_local_info_title, municipio), frase)
    }

    /**
     * Brief de conducción de zona (combustible, peajes, restricciones) al llegar a un
     * lugar nuevo. El detalle va a una notificación legible (multi-línea); la voz solo
     * anuncia que está listo — leer 5 viñetas en voz alta mientras se rueda es peor.
     */
    fun announceZoneBrief(place: String, body: String) {
        if (!enabled) return
        postSilentInfoNotification(ZONE_BRIEF_NOTIFICATION_ID, resolver.get(R.string.obd_zone_brief_title, place), body)
        _alerts.tryEmit(ObdAlert(AlertType.ZONE_BRIEF, resolver.get(R.string.obd_zone_brief_ready, place), 0.0))
        if (voiceZoneBrief) speak(resolver.get(R.string.obd_zone_brief_available, place))
    }

    /**
     * Transparencia de las reglas de restricción generadas por IA (dato legal — el
     * usuario debe poder revisarlas): notificación silenciosa con la rotación + aviso
     * corto por voz gated por la categoría de pico y placa.
     */
    fun announceAiRulesUpdated(rules: PicoYPlacaEngine.CityRules) {
        if (!enabled) return
        val title = resolver.get(R.string.obd_ai_rules_updated, rules.displayName)
        Timber.i("AlertsEngine: $title")
        _alerts.tryEmit(ObdAlert(AlertType.PICO_Y_PLACA, title, 0.0))
        postSilentInfoNotification(AI_RULES_NOTIFICATION_ID, title, CityRulesFormatter.resumen(rules, resolver))
        if (voicePicoPlaca) speak(title)
    }

    private fun postSilentInfoNotification(notificationId: Int, title: String, body: String) {
        runCatching {
            createLocalInfoChannel()
            val notification = NotificationCompat.Builder(context, LOCAL_INFO_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_revscope)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(notificationId, notification)
        }.onFailure { Timber.w(it, "AlertsEngine: info notification failed") }
    }

    private fun createLocalInfoChannel() {
        val channel = NotificationChannel(
            LOCAL_INFO_CHANNEL_ID,
            resolver.get(R.string.obd_local_info_channel),
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
        val message = resolver.get(R.string.obd_mil_alert)
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

    /** Spoken lap time — "Vuelta 3: 1 minuto 42.5" over the helmet intercom. */ // i18n-ignore
    fun announceLap(lapNumber: Int, timeMs: Long) {
        if (!voiceSport) return
        val phrase = lapPhrase(resolver, lapNumber, timeMs)
        Timber.i("AlertsEngine: $phrase")
        speak(phrase)
    }

    /** Spoken 0-100 result — reaches the helmet intercom over the media stream. */
    fun announceLaunch(to60Ms: Long?, to100Ms: Long?, to60MphMs: Long?) {
        if (!voiceSport) return
        val phrase = launchPhrase(resolver, to60Ms, to100Ms, to60MphMs, units.system) ?: return
        Timber.i("AlertsEngine: $phrase")
        speak(phrase)
    }

    private fun sanitizeForSpeech(text: String): String {
        // Strip URLs (https?://\S+)
        var result = text.replace(Regex("https?://\\S+"), "")
        // Strip markdown chars: *, _, #, backticks
        result = result.replace(Regex("[*_#`]"), "")
        // Collapse whitespace
        result = result.replace(Regex("\\s+"), " ")
        // Trim
        return result.trim()
    }

    private fun speak(text: String) {
        if (!enabled || !ttsEnabled) return
        speakUnconditionally(text)
    }

    /**
     * Habla sin consultar los interruptores de alertas. Reservado para la cuenta regresiva de
     * emergencia — ver [announceCrashCountdown]. Usa QUEUE_FLUSH: la cuenta regresiva no puede
     * quedar detrás de un aviso de radar en la cola.
     */
    private fun speakUnconditionally(text: String) {
        runCatching {
            // Tocar el lazy arranca la inicialización si nadie habló todavía en esta sesión;
            // si aún no está listo, el siguiente tick de la cuenta regresiva sí sonará.
            val engine = tts
            if (ttsReady) {
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "revscope_alert")
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
