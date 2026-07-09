package com.revscope.core.obd.wearlink

/**
 * Data Layer payload contract for heart-rate messages from the watch:
 * UTF-8 "timestampMs;bpm". Shared pure parser so both sides stay in sync.
 */
object HrPayload {

    fun parse(data: ByteArray): Pair<Long, Float>? {
        val parts = String(data, Charsets.UTF_8).split(";")
        if (parts.size != 2) return null
        val timestamp = parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val bpm = parts[1].toFloatOrNull()?.takeIf { it > 0f } ?: return null
        return timestamp to bpm
    }
}
