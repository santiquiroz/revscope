package com.revscope.core.intelligence.efficiency

import com.revscope.core.intelligence.R

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

/** [labelRes] is a string resource id (R.string.ai_drive_style_*). */
enum class DriveStyle(val labelRes: Int) {
    ECO(R.string.ai_drive_style_eco),
    NORMAL(R.string.ai_drive_style_normal),
    SPORT(R.string.ai_drive_style_sport),
    AGGRESSIVE(R.string.ai_drive_style_aggressive);

    companion object {
        fun fromScore(score: Int): DriveStyle = when {
            score >= 80 -> ECO
            score >= 60 -> NORMAL
            score >= 40 -> SPORT
            else -> AGGRESSIVE
        }
    }
}
