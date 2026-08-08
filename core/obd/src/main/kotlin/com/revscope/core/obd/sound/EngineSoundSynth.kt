package com.revscope.core.obd.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Sintetizador aditivo de sonido de motor — DSP puro, sin dependencias Android.
 * Genera PCM 16-bit mono siguiendo las RPM reales: armónicos de la frecuencia de
 * encendido + rumble subarmónico + ruido de escape modulado por los pulsos de encendido.
 */
class EngineSoundSynth(private val sampleRate: Int = SAMPLE_RATE) {

    private var pack: SoundPack = SoundPack.DEFAULT
    private var phase = 0.0
    private var detunePhase = 0.0
    private var smoothedRpm = 0.0
    private var jitter = 0.0
    private val random = Random(0x5EED)

    fun setPack(newPack: SoundPack) {
        pack = newPack
    }

    fun reset() {
        phase = 0.0
        detunePhase = 0.0
        smoothedRpm = 0.0
        jitter = 0.0
    }

    /**
     * Llena [buffer] con el sonido del pack activo. [targetRpm] son las RPM crudas del
     * OBD (0 o NaN = motor apagado → silencio con fade). [throttle01] modula volumen y
     * agresividad; con -1 (sin dato) se deriva de las RPM.
     */
    fun render(buffer: ShortArray, targetRpm: Double, throttle01: Double) {
        val safeRpm = if (targetRpm.isFinite()) max(0.0, targetRpm) else 0.0
        val chunkMs = buffer.size * 1000.0 / sampleRate
        smoothedRpm = smoothRpm(smoothedRpm, safeRpm, chunkMs)

        if (smoothedRpm < MIN_AUDIBLE_RPM) {
            buffer.fill(0)
            return
        }

        updateIdleJitter()
        val load = resolveLoad(throttle01, smoothedRpm)
        val amplitude = (BASE_LEVEL + LOAD_LEVEL * load) * MASTER_LEVEL
        val firingHz = firingFrequencyHz(smoothedRpm, pack.cylinders) * pack.freqScale * (1.0 + jitter)
        val phaseStep = firingHz / sampleRate
        val detuneStep = phaseStep * (1.0 + pack.detune)

        for (i in buffer.indices) {
            var sample = toneAt(phase)
            if (pack.detune > 0.0) sample = 0.6 * sample + 0.4 * toneAt(detunePhase)
            sample += pack.noiseAmount * exhaustNoise(phase)
            buffer[i] = (tanh(sample * amplitude) * Short.MAX_VALUE).toInt().toShort()
            phase = (phase + phaseStep) % 1.0
            detunePhase = (detunePhase + detuneStep) % 1.0
        }
    }

    private fun toneAt(p: Double): Double {
        if (pack.square) {
            return if (sin(TWO_PI * p) >= 0.0) 0.9 else -0.9
        }
        var sample = 0.0
        for (k in pack.harmonics.indices) {
            sample += pack.harmonics[k] * sin(TWO_PI * (k + 1) * p)
        }
        sample += pack.subharmonic * sin(TWO_PI * 0.5 * p)
        return sample / harmonicNorm()
    }

    // Ruido blanco con envolvente rítmica atada al pulso de encendido — la "explosión"
    // por cilindro es lo que hace que el escape suene a motor y no a estática.
    private fun exhaustNoise(p: Double): Double {
        val pulse = max(0.0, sin(TWO_PI * p)).pow(4)
        return (random.nextDouble() * 2.0 - 1.0) * (0.25 + 0.75 * pulse)
    }

    private fun harmonicNorm(): Double = pack.harmonics.sum() + pack.subharmonic + 1e-9

    // Ralentí "lopey": paseo aleatorio lento en la frecuencia, se desvanece sobre 2000 RPM.
    private fun updateIdleJitter() {
        val idleFactor = max(0.0, 1.0 - smoothedRpm / 2_000.0)
        jitter += (random.nextDouble() * 2.0 - 1.0) * pack.idleJitter * 0.3
        jitter = jitter.coerceIn(-pack.idleJitter, pack.idleJitter) * (0.9 + 0.1 * idleFactor)
        if (idleFactor <= 0.0) jitter *= 0.7
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val CHUNK_SAMPLES = 2_048
        const val MIN_AUDIBLE_RPM = 300.0
        private const val TWO_PI = 2.0 * PI
        private const val SMOOTHING_TAU_MS = 120.0
        private const val BASE_LEVEL = 0.35
        private const val LOAD_LEVEL = 0.65
        private const val MASTER_LEVEL = 0.85

        /** Frecuencia de encendido de un 4 tiempos: rpm/60 · cilindros/2. */
        fun firingFrequencyHz(rpm: Double, cylinders: Int): Double =
            rpm / 60.0 * cylinders / 2.0

        /** Suavizado exponencial hacia las RPM objetivo — evita saltos entre muestreos de 100 ms. */
        fun smoothRpm(current: Double, target: Double, dtMs: Double, tauMs: Double = SMOOTHING_TAU_MS): Double {
            val alpha = 1.0 - exp(-dtMs / tauMs)
            return current + (target - current) * alpha
        }

        /** Carga del motor 0..1: throttle real si existe, si no una curva por RPM. */
        fun resolveLoad(throttle01: Double, rpm: Double): Double = when {
            throttle01 in 0.0..1.0 -> throttle01
            else -> (rpm / 7_000.0).coerceIn(0.0, 1.0)
        }
    }
}

/** Diferencia absoluta acotada — helper de tests. */
internal fun nearlyEqual(a: Double, b: Double, epsilon: Double = 1e-9): Boolean = abs(a - b) < epsilon
