package com.revscope.core.intelligence.provider

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.intelligence.R

/**
 * Cuerpo del prompt + una línea final con el idioma de respuesta. El cuerpo sigue en
 * español (los centinelas como NADA/NONE dependen de él); solo la respuesta cambia de idioma.
 */
internal fun String.withResponseLanguage(resolver: StringResolver): String =
    this + "\n" + resolver.get(R.string.ai_response_language)
