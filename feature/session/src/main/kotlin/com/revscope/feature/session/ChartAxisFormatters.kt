package com.revscope.feature.session

import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.revscope.core.common.format.formatElapsedMmSs

/**
 * Bottom-axis formatter for report charts whose series have no per-sample timestamp
 * (only value lists) — assumes samples were taken at roughly even intervals across
 * [durationMs], so tick index maps linearly to elapsed trip time.
 */
internal fun elapsedFractionFormatter(sampleCount: Int, durationMs: Long): CartesianValueFormatter {
    val totalSeconds = (durationMs / 1000.0).coerceAtLeast(1.0)
    val lastIndex = (sampleCount - 1).coerceAtLeast(1)
    return object : CartesianValueFormatter {
        override fun format(
            context: CartesianMeasuringContext,
            value: Double,
            verticalAxisPosition: Axis.Position.Vertical?,
        ): CharSequence = formatElapsedMmSs((value / lastIndex) * totalSeconds)
    }
}
