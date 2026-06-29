package com.revscope.core.common.ext

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

/** Logs errors from a Flow without terminating the stream. */
fun <T> Flow<T>.catchAndLog(tag: String): Flow<T> =
    catch { e -> Timber.e(e, "$tag: unhandled error") }

/** Logs each emitted value at VERBOSE level. */
fun <T> Flow<T>.logEach(tag: String): Flow<T> =
    onEach { Timber.v("$tag: $it") }
