package com.revscope.feature.settings

import com.revscope.feature.settings.SettingsViewModel.SaveResult

internal fun aiSaveResult(outcome: Result<Boolean>, keyEntered: Boolean): SaveResult {
    val keyStored = outcome.getOrElse { return SaveResult(false, "Error guardando la configuración de IA") }
    if (!keyStored) {
        return SaveResult(
            false,
            "No se pudo guardar la API key: el almacén seguro del teléfono no está disponible. No se activaron las funciones IA",
        )
    }
    val extra = if (keyEntered) " — funciones IA activadas" else ""
    return SaveResult(true, "Configuración de IA guardada$extra")
}
