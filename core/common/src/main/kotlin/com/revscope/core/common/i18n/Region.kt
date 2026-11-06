package com.revscope.core.common.i18n

import android.content.res.Resources
import java.util.Locale

enum class Region {
    CO,
    US,
    GB,
    OTHER;

    companion object {
        fun fromCountry(country: String): Region = when (country.uppercase(Locale.ROOT)) {
            "CO" -> CO
            "US" -> US
            "GB" -> GB
            else -> OTHER
        }

        fun system(): Region = fromCountry(Resources.getSystem().configuration.locales[0].country)
    }
}
