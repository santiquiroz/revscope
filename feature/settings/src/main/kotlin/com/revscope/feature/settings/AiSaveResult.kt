package com.revscope.feature.settings

import com.revscope.core.common.i18n.StringResolver
import com.revscope.feature.settings.SettingsViewModel.SaveResult

internal fun aiSaveResult(
    outcome: Result<Boolean>,
    keyEntered: Boolean,
    resolver: StringResolver,
): SaveResult {
    val keyStored = outcome.getOrElse {
        return SaveResult(false, resolver.get(R.string.settings_ai_save_error))
    }
    if (!keyStored) {
        return SaveResult(
            false,
            resolver.get(R.string.settings_ai_secure_storage_error),
        )
    }
    val message = if (keyEntered) {
        resolver.get(R.string.settings_ai_save_success_enabled)
    } else {
        resolver.get(R.string.settings_ai_save_success)
    }
    return SaveResult(true, message)
}
