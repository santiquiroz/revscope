package com.revscope.feature.settings

import com.revscope.core.common.net.NetworkErrorExplainer
import com.revscope.feature.settings.SettingsViewModel.SaveResult

private const val MAX_DETAIL_CHARS = 200

internal fun aiConnectionFailure(error: Throwable): SaveResult =
    SaveResult(false, "No se pudo conectar — ${connectionFailureDetail(error)}")

private fun connectionFailureDetail(error: Throwable): String =
    NetworkErrorExplainer.explain(error)
        ?: error.message?.take(MAX_DETAIL_CHARS)
        ?: "revisa la llave y la red"
