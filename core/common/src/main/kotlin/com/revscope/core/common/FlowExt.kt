package com.revscope.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

/**
 * stateIn con contención: si la fuente (típicamente Room) lanza, se loggea y el StateFlow
 * conserva el último valor en vez de matar el proceso. Corrupción de DB degrada, no crashea.
 */
fun <T> Flow<T>.stateInSafe(
    scope: CoroutineScope,
    initialValue: T,
    started: SharingStarted = SharingStarted.WhileSubscribed(5_000),
): StateFlow<T> = this
    .catch { Timber.w(it, "stateInSafe: la fuente falló — se conserva el último valor") }
    .stateIn(scope, started, initialValue)
