package com.revscope.core.obd.mcp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.revscope.core.obd.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Aviso en el teléfono cuando un cliente MCP cambia algo en el vehículo (p. ej. borra los DTC). */
@Singleton
class McpActionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun avisar(titulo: String, texto: String) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Acciones del servidor MCP", NotificationManager.IMPORTANCE_DEFAULT),
            )
            manager.notify(NOTIFICATION_ID, notificacion(titulo, texto))
        }.onFailure { Timber.w(it, "McpActionNotifier: no se pudo publicar el aviso") }
    }

    private fun notificacion(titulo: String, texto: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setAutoCancel(true)
            .build()

    private companion object {
        const val CHANNEL_ID = "revscope_mcp_actions"
        const val NOTIFICATION_ID = 3001
    }
}
