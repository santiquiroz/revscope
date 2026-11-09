package com.revscope.core.common.net

import com.revscope.core.common.R
import com.revscope.core.common.i18n.StringResolver

object NetworkErrorExplainer {

    private const val CLEARTEXT_MARKER = "cleartext"

    fun explain(error: Throwable, resolver: StringResolver): String? =
        if (causeChain(error).any(::isCleartextBlocked)) resolver.get(R.string.common_cleartext_blocked) else null

    // Solo el mensaje: OkHttp también lanza UnknownServiceException por fallos de TLS.
    private fun isCleartextBlocked(error: Throwable): Boolean =
        error.message?.contains(CLEARTEXT_MARKER, ignoreCase = true) == true

    private fun causeChain(error: Throwable): Sequence<Throwable> =
        generateSequence(error) { current -> current.cause?.takeIf { it !== current } }
}
