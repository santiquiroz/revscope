package com.revscope.core.obd.legal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Corre a diario a las 5:30am (America/Bogota, ver RevScopeApp) y postea una sola
 * notificación resumen si algún vehículo tiene pico y placa hoy o un documento por
 * vencer en 30/15/7/1/0 días (o ya vencido). Sin datos notables → no notifica.
 */
@HiltWorker
class DailyStatusWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val vehicleProfileDao: VehicleProfileDao,
    private val settings: DataStore<Preferences>,
    private val aiRulesSource: RestrictionRulesSource,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profiles = vehicleProfileDao.observeAll().first()
        val license = readLicenseExpiresAt()
        val overrideRules = readPicoYPlacaOverrideRules()
        val nowMs = System.currentTimeMillis()

        val lines = profiles.mapNotNull { profile -> notableLineFor(profile, license, overrideRules, nowMs) }
        if (lines.isEmpty()) return Result.success()

        postSummaryNotification(lines)
        return Result.success()
    }

    private suspend fun readLicenseExpiresAt(): Long? =
        settings.data.first()[PreferencesKeys.LICENSE_EXPIRES_AT]

    /** Edición manual del usuario; [DocumentStatusCalculator] la usa solo si su cityId coincide con el perfil. */
    private suspend fun readPicoYPlacaOverrideRules(): PicoYPlacaEngine.CityRules? =
        settings.data.first()[PreferencesKeys.PICO_PLACA_RULES_JSON]?.let(PicoYPlacaEngine::parseRulesJson)

    private suspend fun notableLineFor(
        profile: VehicleProfileEntity,
        license: Long?,
        overrideRules: PicoYPlacaEngine.CityRules?,
        nowMs: Long,
    ): String? {
        val documents = DocumentStatusCalculator.fromProfile(profile, license)
        val aiFallback = aiFallbackFor(profile.picoPlacaCity, overrideRules, nowMs)
        val statuses = DocumentStatusCalculator.calculate(documents, overrideRules, nowMs, aiFallbackRules = aiFallback)
        val notable = statuses.filter(::isNotable)
        if (notable.isEmpty()) return null
        val facts = notable.joinToString(" · ") { it.detalle }
        return "${emojiFor(profile)} ${profile.name}: $facts"
    }

    private suspend fun aiFallbackFor(
        cityId: String?,
        overrideRules: PicoYPlacaEngine.CityRules?,
        nowMs: Long,
    ): PicoYPlacaEngine.CityRules? =
        cityId
            ?.takeIf { DocumentStatusCalculator.needsAiFallback(it, overrideRules, nowMs) }
            ?.let { runCatching { aiRulesSource.rulesForCity(it) }.getOrNull() }

    private fun emojiFor(profile: VehicleProfileEntity): String =
        if (profile.type == "MOTORCYCLE") "🏍" else "🚗"

    private fun postSummaryNotification(lines: List<String>) {
        createChannel()
        val pending = buildContentIntent() ?: return
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle("Vehículo al día")
            .setContentText(lines.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }.onFailure { Timber.w(it, "DailyStatusWorker: could not post summary") }
    }

    private fun buildContentIntent(): PendingIntent? {
        val launch = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.putExtra(EXTRA_OPEN_AL_DIA, true)
            ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Documentos del vehículo", NotificationManager.IMPORTANCE_DEFAULT,
        )
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_OPEN_AL_DIA = "open_al_dia"
        const val WORK_NAME = "daily_status"
        private const val CHANNEL_ID = "revscope_documentos"
        private const val NOTIFICATION_ID = 3001
        private val NOTABLE_DAYS = setOf(30L, 15L, 7L, 1L, 0L)

        private fun isNotable(status: DocumentStatusCalculator.DocStatus): Boolean = when {
            status.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA -> isPicoYPlacaRestrictedToday(status)
            status.nivel == DocumentStatusCalculator.Nivel.VENCIDO -> true
            status.nivel == DocumentStatusCalculator.Nivel.ATENCION -> status.diasRestantes in NOTABLE_DAYS
            else -> false
        }

        private fun isPicoYPlacaRestrictedToday(status: DocumentStatusCalculator.DocStatus): Boolean =
            status.nivel == DocumentStatusCalculator.Nivel.VENCIDO || status.nivel == DocumentStatusCalculator.Nivel.ATENCION
    }
}
