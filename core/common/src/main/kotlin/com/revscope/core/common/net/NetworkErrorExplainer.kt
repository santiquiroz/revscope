package com.revscope.core.common.net

object NetworkErrorExplainer {

    const val CLEARTEXT_BLOCKED =
        "Android bloquea http:// fuera de este teléfono: usa https (p. ej. un proxy con certificado) o un túnel"

    private const val CLEARTEXT_MARKER = "cleartext"

    fun explain(error: Throwable): String? =
        if (causeChain(error).any(::isCleartextBlocked)) CLEARTEXT_BLOCKED else null

    // Solo el mensaje: OkHttp también lanza UnknownServiceException por fallos de TLS.
    private fun isCleartextBlocked(error: Throwable): Boolean =
        error.message?.contains(CLEARTEXT_MARKER, ignoreCase = true) == true

    private fun causeChain(error: Throwable): Sequence<Throwable> =
        generateSequence(error) { current -> current.cause?.takeIf { it !== current } }
}
