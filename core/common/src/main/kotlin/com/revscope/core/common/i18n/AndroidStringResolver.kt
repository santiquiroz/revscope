package com.revscope.core.common.i18n

import android.content.Context
import java.util.Locale

class AndroidStringResolver(context: Context) : StringResolver {
    private val appContext = context.applicationContext
    private val localizedContexts = mutableMapOf<String, Context>()

    override val locale: Locale
        get() = AppLanguage.current(appContext)

    override fun get(id: Int, vararg args: Any): String =
        localized().getString(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String =
        localized().resources.getQuantityString(id, count, *args)

    private fun localized(): Context {
        val current = locale
        return synchronized(localizedContexts) {
            localizedContexts.getOrPut(current.toLanguageTag()) { AppLanguage.localizedContext(appContext, current) }
        }
    }
}
