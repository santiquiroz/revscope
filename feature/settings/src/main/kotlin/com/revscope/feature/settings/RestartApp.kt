package com.revscope.feature.settings

import android.content.Context
import android.content.Intent

/**
 * Reinicia el proceso tras reemplazar revscope.db: la instancia actual de AppDatabase queda inutilizable.
 */
fun restartApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: return
    context.startActivity(Intent.makeRestartActivityTask(launchIntent.component))
    Runtime.getRuntime().exit(0)
}
