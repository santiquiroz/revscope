package com.revscope.core.obd.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.obd.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the dismissable "trip saved" summary after a clean automatic shutdown. */
@Singleton
class TripSummaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun post(session: SessionEntity) {
        if (!shouldNotify(session)) return
        createChannel()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_SESSION_ID, session.id)
            ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, session.id.toInt(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle("Viaje guardado")
            .setContentText(summaryText(session))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }.onFailure { Timber.w(it, "TripSummaryNotifier: could not post summary") }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Resumen de viaje", NotificationManager.IMPORTANCE_DEFAULT,
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_SESSION_ID = "open_session_id"
        private const val CHANNEL_ID = "revscope_trip_summary"
        private const val NOTIFICATION_ID = 2001
        private const val MIN_NOTIFY_DISTANCE_KM = 0.2f
        private val LOCALE_ES = Locale("es", "CO")

        fun summaryText(session: SessionEntity): String {
            val minutes = ((session.endedAt ?: session.startedAt) - session.startedAt) / 60_000
            return String.format(
                LOCALE_ES, "%.1f km · %d km/h máx · %d min",
                session.distanceKm, session.maxSpeed, minutes,
            )
        }

        fun shouldNotify(session: SessionEntity): Boolean =
            session.distanceKm >= MIN_NOTIFY_DISTANCE_KM
    }
}
