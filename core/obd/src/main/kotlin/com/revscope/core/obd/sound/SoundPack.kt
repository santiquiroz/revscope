package com.revscope.core.obd.sound

/**
 * Catálogo de sonidos de motor sintetizados. Cada pack es un perfil DSP: no hay
 * grabaciones — todo se genera en tiempo real siguiendo las RPM reales del OBD.
 */
enum class SoundPack(
    val id: String,
    val displayName: String,
    val cylinders: Int,
    val harmonics: DoubleArray,
    val subharmonic: Double,
    val noiseAmount: Double,
    val idleJitter: Double,
    val freqScale: Double,
    val square: Boolean,
    val detune: Double,
    val funny: Boolean,
) {
    V8_MUSCLE(
        id = "v8_muscle", displayName = "V8 muscle americano", cylinders = 8,
        harmonics = doubleArrayOf(1.0, 0.55, 0.7, 0.35, 0.25, 0.15),
        subharmonic = 0.6, noiseAmount = 0.18, idleJitter = 0.06,
        freqScale = 1.0, square = false, detune = 0.0, funny = false,
    ),
    V10_F1(
        id = "v10_f1", displayName = "V10 Fórmula 1", cylinders = 10,
        harmonics = doubleArrayOf(0.8, 1.0, 0.7, 0.6, 0.5, 0.4, 0.3, 0.25),
        subharmonic = 0.1, noiseAmount = 0.10, idleJitter = 0.01,
        freqScale = 1.6, square = false, detune = 0.0, funny = false,
    ),
    V12_GT(
        id = "v12_gt", displayName = "V12 gran turismo", cylinders = 12,
        harmonics = doubleArrayOf(0.9, 0.8, 1.0, 0.5, 0.45, 0.3, 0.2),
        subharmonic = 0.2, noiseAmount = 0.12, idleJitter = 0.02,
        freqScale = 1.0, square = false, detune = 0.0, funny = false,
    ),
    FART_ENGINE(
        id = "fart_engine", displayName = "Motor flatulento", cylinders = 2,
        harmonics = doubleArrayOf(1.0, 0.3),
        subharmonic = 0.8, noiseAmount = 0.75, idleJitter = 0.25,
        freqScale = 0.5, square = false, detune = 0.0, funny = true,
    ),
    PODRACER(
        id = "podracer", displayName = "Podracer galáctico", cylinders = 4,
        harmonics = doubleArrayOf(1.0, 0.2, 0.8, 0.15, 0.9, 0.1, 0.6),
        subharmonic = 0.05, noiseAmount = 0.08, idleJitter = 0.03,
        freqScale = 6.0, square = false, detune = 0.03, funny = true,
    ),
    ARCADE_8BIT(
        id = "arcade_8bit", displayName = "Arcade 8-bit", cylinders = 4,
        harmonics = doubleArrayOf(1.0),
        subharmonic = 0.0, noiseAmount = 0.03, idleJitter = 0.0,
        freqScale = 2.0, square = true, detune = 0.0, funny = true,
    ),
    ;

    companion object {
        val DEFAULT = V8_MUSCLE

        fun fromId(id: String?): SoundPack = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
