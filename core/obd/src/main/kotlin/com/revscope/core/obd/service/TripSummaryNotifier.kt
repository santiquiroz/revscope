package com.revscope.core.obd.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.revscope.core.common.i18n.Region
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.i18n.detect
import com.revscope.core.common.i18n.formatRegionalMoney
import com.revscope.core.common.i18n.systemLocale
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.obd.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the dismissable "trip saved" summary after a clean automatic shutdown. */ // i18n-ignore
@Singleton
class TripSummaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: StringResolver,
    private val units: UnitFormatter,
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
            .setContentTitle(resolver.get(R.string.obd_trip_saved))
            .setContentText(summaryText(session, resolver, units, ::formatCost))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }.onFailure { Timber.w(it, "TripSummaryNotifier: could not post summary") }
    }

    private fun formatCost(amount: Double): String =
        formatRegionalMoney(amount, Region.detect(context), systemLocale(), resolver.locale)

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, resolver.get(R.string.obd_trip_summary_channel), NotificationManager.IMPORTANCE_DEFAULT,
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_SESSION_ID = "open_session_id"
        private const val CHANNEL_ID = "revscope_trip_summary"
        private const val NOTIFICATION_ID = 2001
        private const val MIN_NOTIFY_DISTANCE_KM = 0.2f
        fun summaryText(
            session: SessionEntity,
            resolver: StringResolver,
            units: UnitFormatter,
            formatCost: (Double) -> String,
        ): String {
            val minutes = ((session.endedAt ?: session.startedAt) - session.startedAt) / 60_000
            val base = resolver.get(
                R.string.obd_trip_summary_format,
                units.formatDistance(session.distanceKm.toDouble()),
                units.formatSpeed(session.maxSpeed.toDouble()),
                minutes,
            )
            val extras = buildList {
                session.fuelCostCop?.let { add(resolver.get(R.string.obd_trip_summary_cost, formatCost(it))) }
                session.ecoScore?.let { add(resolver.get(R.string.obd_trip_summary_eco, it)) }
            }
            return (listOf(base) + extras).joinToString(" · ")
        }

        fun shouldNotify(session: SessionEntity): Boolean =
            session.distanceKm >= MIN_NOTIFY_DISTANCE_KM
    }
}
