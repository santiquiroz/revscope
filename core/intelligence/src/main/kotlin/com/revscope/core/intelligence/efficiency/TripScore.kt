package com.revscope.core.intelligence.efficiency

/**
 * Result of a trip scored by [DriveStyleClassifier].
 *
 * @property overall              0–100 efficiency index (100 = most efficient)
 * @property style                Qualitative label derived from [overall]
 * @property highRpmTimePercent   % of time engine spent above 80% redline
 * @property hardAccelerationCount Number of throttle bursts > 50% in < 2 s
 * @property avgEngineLoad        Average engine load % over the trip
 */
data class TripScore(
    val overall: Int,
    val style: DriveStyle,
    val highRpmTimePercent: Float,
    val hardAccelerationCount: Int,
    val avgEngineLoad: Float,
) {
    companion object {
        fun empty() = TripScore(
            overall = 100,
            style = DriveStyle.ECO,
            highRpmTimePercent = 0f,
            hardAccelerationCount = 0,
            avgEngineLoad = 0f,
        )
    }
}

enum class DriveStyle(val label: String, val emoji: String) {
    ECO("Eco", "🌿"),
    NORMAL("Normal", "🚗"),
    SPORT("Sport", "🏎️"),
    AGGRESSIVE("Agresivo", "🔥");

    companion object {
        fun fromScore(score: Int): DriveStyle = when {
            score >= 80 -> ECO
            score >= 60 -> NORMAL
            score >= 40 -> SPORT
            else -> AGGRESSIVE
        }
    }
}
