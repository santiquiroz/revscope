package com.revscope.core.obd.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.revscope.core.obd.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val CHANNEL_ID = "revscope_mcp_server"
private const val NOTIFICATION_ID = 4001

/**
 * Lightweight foreground service hosting the local-network MCP server (plan6 Task 4) — its own
 * notification channel, independent from [com.revscope.core.obd.service.ObdForegroundService]:
 * the MCP server has nothing to do with telemetry recording and must keep running (or not)
 * regardless of whether a trip is active. Only alive while the toggle in Settings is on and the
 * app process is alive — no persistence across reboots (see plan6 Task 4 constraints).
 */
@AndroidEntryPoint
class McpServerService : Service() {

    @Inject lateinit var controller: McpServerController
    @Inject lateinit var tokenStore: McpTokenStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando servidor MCP…"))
        scope.launch { startServer() }
        Timber.i("McpServerService: started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.stop()
        scope.cancel()
        Timber.i("McpServerService: destroyed")
        super.onDestroy()
    }

    private suspend fun startServer() {
        val token = tokenStore.tokenOrGenerate()
        val started = controller.start(token)
        updateNotification()
        if (!started) stopSelf()
    }

    private fun updateNotification() {
        val text = notificationTextFor(controller.state.value)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle("Servidor MCP")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Detener", stopPendingIntent())
            .build()

    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(this, McpServerService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Servidor MCP", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_STOP = "com.revscope.core.obd.action.MCP_STOP"

        fun notificationTextFor(state: McpServerState): String = when (state) {
            is McpServerState.Running -> "Servidor MCP activo en ${state.url}"
            McpServerState.NoWifi -> "Sin WiFi — conecta a una red para activar el servidor MCP"
            McpServerState.Stopped -> "No se pudo iniciar el servidor MCP"
        }

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, McpServerService::class.java))
            }.onFailure { Timber.w(it, "McpServerService: could not start (app in background?)") }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, McpServerService::class.java))
        }
    }
}
