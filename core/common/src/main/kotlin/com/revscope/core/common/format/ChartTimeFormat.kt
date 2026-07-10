package com.revscope.core.common.format

/** Formats elapsed seconds as "mm:ss" for chart time axes (clamped to non-negative). */
fun formatElapsedMmSs(seconds: Double): String {
    val totalSeconds = seconds.toLong().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%d:%02d".format(minutes, secs)
}
