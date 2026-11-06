package com.revscope.core.common.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    val SUPPORTED = listOf("en", "es")

    internal const val PREFS = "revscope_language"
    private const val KEY_TAG = "tag"

    fun resolve(savedTag: String, systemLocale: Locale): Locale {
        val language = if (savedTag.isEmpty()) systemLocale.language else Locale.forLanguageTag(savedTag).language
        return Locale.forLanguageTag(SUPPORTED.firstOrNull { it == language } ?: SUPPORTED.first())
    }

    fun savedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, "") ?: ""

    fun current(context: Context): Locale {
        val systemLocale = Resources.getSystem().configuration.locales[0]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!appLocales.isEmpty) return resolve(appLocales[0].toLanguageTag(), systemLocale)
        }
        return resolve(savedTag(context), systemLocale)
    }

    fun localizedContext(context: Context): Context = localizedContext(context, current(context))

    fun localizedContext(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun persist(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TAG, tag).commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }
}
