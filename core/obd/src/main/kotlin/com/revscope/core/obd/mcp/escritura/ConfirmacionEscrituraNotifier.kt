package com.revscope.core.obd.mcp.escritura

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.revscope.core.obd.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Notificación de alta prioridad con Permitir/Rechazar; deslizarla también rechaza. */
@Singleton
class ConfirmacionEscrituraNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : PedidorConfirmacion {

    @SuppressLint("MissingPermission") // areNotificationsEnabled() cubre el permiso POST_NOTIFICATIONS
    override fun pedir(id: Int, solicitud: SolicitudEscritura, esperaMs: Long): Boolean {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return false
        return runCatching {
            crearCanal()
            compat.notify(TAG, id, notificacion(id, solicitud, esperaMs))
            true
        }.onFailure { Timber.w(it, "ConfirmacionEscrituraNotifier: no se pudo publicar") }.getOrDefault(false)
    }

    override fun retirar(id: Int) {
        NotificationManagerCompat.from(context).cancel(TAG, id)
    }

    private fun notificacion(id: Int, solicitud: SolicitudEscritura, esperaMs: Long) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle("La IA pide escribir en el vehículo")
            .setContentText(solicitud.resumen)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detalle(solicitud)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setTimeoutAfter(esperaMs)
            .setAutoCancel(true)
            .addAction(0, "Permitir", decision(id, permitido = true))
            .addAction(0, "Rechazar", decision(id, permitido = false))
            .setDeleteIntent(decision(id, permitido = false))
            .build()

    private fun detalle(solicitud: SolicitudEscritura): String = buildString {
        appendLine(solicitud.resumen)
        appendLine("Tool: ${solicitud.tool}")
        appendLine("Módulo: ${solicitud.header ?: "por defecto (7DF/motor)"}")
        append("Comandos: ${solicitud.pasos.joinToString(" → ")}")
    }

    private fun decision(id: Int, permitido: Boolean): PendingIntent {
        val intent = Intent(context, ConfirmacionEscrituraReceiver::class.java)
            .setAction(ConfirmacionEscrituraReceiver.ACTION)
            .putExtra(ConfirmacionEscrituraReceiver.EXTRA_ID, id)
            .putExtra(ConfirmacionEscrituraReceiver.EXTRA_PERMITIDO, permitido)
        val requestCode = id * 2 + if (permitido) 1 else 0
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun crearCanal() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Confirmaciones de escritura del MCP", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private companion object {
        const val CHANNEL_ID = "revscope_mcp_confirmaciones"
        const val TAG = "mcp_confirmacion"
    }
}
