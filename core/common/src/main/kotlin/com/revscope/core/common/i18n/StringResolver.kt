package com.revscope.core.common.i18n

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

interface StringResolver {
    val locale: Locale
    fun get(@StringRes id: Int, vararg args: Any): String
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}
