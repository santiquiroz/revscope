package com.revscope.core.obd.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reproduce el sonido de motor sintetizado siguiendo las RPM reales (PID 0C) mientras
 * hay sesión de telemetría. Sale por USAGE_MEDIA — igual que las alertas de voz, llega
 * al intercomunicador del casco o al estéreo del carro por Bluetooth.
 */
@Singleton
class EngineSoundController @Inject constructor(
    private val sessionManager: ObdSessionManager,
    settings: DataStore<Preferences>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val synth = EngineSoundSynth()

    @Volatile private var enabled = false
    @Volatile private var volume = DEFAULT_VOLUME
    @Volatile private var pack = SoundPack.DEFAULT

    private var job: Job? = null

    init {
        scope.launch {
            settings.data.collect { prefs ->
                enabled = prefs[PreferencesKeys.ENGINE_SOUND_ENABLED] ?: false
                volume = (prefs[PreferencesKeys.ENGINE_SOUND_VOLUME] ?: DEFAULT_VOLUME).coerceIn(0, 100)
                pack = SoundPack.fromId(prefs[PreferencesKeys.ENGINE_SOUND_PACK])
            }
        }
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
        Timber.i("EngineSoundController: started")
    }

    fun stop() {
        job?.cancel()
        job = null
        Timber.i("EngineSoundController: stopped")
    }

    private suspend fun runLoop() {
        var track: AudioTrack? = null
        val buffer = ShortArray(EngineSoundSynth.CHUNK_SAMPLES)
        try {
            while (currentCoroutineIsActive()) {
                if (!enabled) {
                    track = releaseTrack(track)
                    synth.reset()
                    delay(IDLE_POLL_MS)
                    continue
                }
                if (track == null) {
                    track = runCatching { buildTrack() }.getOrNull()
                    if (track == null) {
                        delay(IDLE_POLL_MS)
                        continue
                    }
                    track.play()
                }
                synth.setPack(pack)
                track.setVolume(volume / 100f)
                val readings = sessionManager.readings.value
                val rpm = readings[RPM_PID]?.value ?: 0.0
                val throttle = readings[THROTTLE_PID]?.value?.div(100.0) ?: -1.0
                synth.render(buffer, rpm, throttle)
                // El write bloqueante marca el ritmo del loop (~46 ms por chunk).
                track.write(buffer, 0, buffer.size)
            }
        } finally {
            releaseTrack(track)
            synth.reset()
        }
    }

    private fun currentCoroutineIsActive(): Boolean = job?.isActive == true && scope.isActive

    private fun releaseTrack(track: AudioTrack?): AudioTrack? {
        runCatching {
            track?.stop()
            track?.release()
        }
        return null
    }

    private fun buildTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            EngineSoundSynth.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferBytes = maxOf(minBuffer, EngineSoundSynth.CHUNK_SAMPLES * 2 * 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(EngineSoundSynth.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    companion object {
        private const val RPM_PID = "0C"
        private const val THROTTLE_PID = "11"
        private const val DEFAULT_VOLUME = 70
        private const val IDLE_POLL_MS = 500L
    }
}
