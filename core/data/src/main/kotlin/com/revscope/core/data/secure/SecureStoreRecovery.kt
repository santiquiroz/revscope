package com.revscope.core.data.secure

import timber.log.Timber

internal fun <T : Any> openWithRecovery(create: () -> T, wipe: () -> Unit): T? =
    tryOpen(create, "first attempt")
        ?: wipeAndRetry(create, wipe)

private fun <T : Any> wipeAndRetry(create: () -> T, wipe: () -> Unit): T? {
    val wiped = try {
        wipe()
        true
    } catch (e: Exception) {
        Timber.e(e, "SecureKeyStore: wipe of unreadable store failed")
        false
    }
    return if (wiped) tryOpen(create, "retry after wipe") else null
}

private fun <T : Any> tryOpen(create: () -> T, attempt: String): T? = try {
    create()
} catch (e: Exception) {
    Timber.e(e, "SecureKeyStore: init failed ($attempt)")
    null
}
