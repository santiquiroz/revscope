package com.revscope.feature.settings

import com.revscope.core.common.net.NetworkErrorExplainer
import com.revscope.core.common.i18n.StringResolver
import com.revscope.feature.settings.SettingsViewModel.SaveResult

private const val MAX_DETAIL_CHARS = 200

internal fun aiConnectionFailure(error: Throwable, resolver: StringResolver): SaveResult =
    SaveResult(
        false,
        resolver.get(R.string.settings_ai_connection_error, connectionFailureDetail(error, resolver)),
    )

private fun connectionFailureDetail(error: Throwable, resolver: StringResolver): String =
    NetworkErrorExplainer.explain(error, resolver)
        ?: error.message?.take(MAX_DETAIL_CHARS)
        ?: resolver.get(R.string.settings_ai_connection_hint)
