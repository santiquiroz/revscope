package com.revscope.core.common.units

import android.content.Context
import com.revscope.core.common.i18n.AppLanguage
import com.revscope.core.common.i18n.Region

// Vive junto al idioma: cambiar unidades reinicia la app, así que basta una lectura síncrona al arrancar.
object AppUnits {
    private const val KEY_UNITS = "units"

    fun savedTag(context: Context): String =
        context.getSharedPreferences(AppLanguage.PREFS, Context.MODE_PRIVATE).getString(KEY_UNITS, "") ?: ""

    fun current(context: Context): UnitSystem = UnitSystem.fromTag(savedTag(context), Region.system())

    fun persist(context: Context, tag: String) {
        context.getSharedPreferences(AppLanguage.PREFS, Context.MODE_PRIVATE).edit().putString(KEY_UNITS, tag).commit()
    }
}
