package com.revscope.core.common.i18n

import android.content.Context
import android.content.res.Resources
import android.telephony.TelephonyManager
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

        fun system(): Region = fromCountry(systemLocaleCountry())
    }
}

fun Region.Companion.fromSources(simCountry: String?, networkCountry: String?, localeCountry: String): Region {
    val country = listOf(simCountry, networkCountry).firstOrNull { !it.isNullOrBlank() } ?: localeCountry
    return fromCountry(country.trim())
}

fun Region.Companion.detect(context: Context): Region {
    val telephony = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
    return fromSources(
        simCountry = telephony?.let { runCatching { it.simCountryIso }.getOrNull() },
        networkCountry = telephony?.let { runCatching { it.networkCountryIso }.getOrNull() },
        localeCountry = systemLocaleCountry(),
    )
}

private fun systemLocaleCountry(): String = Resources.getSystem().configuration.locales[0].country
