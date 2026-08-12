package com.revscope.core.obd.legal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arma y postea el resumen diario "Vehículo al día": pico y placa de hoy y documentos por
 * vencer en 30/15/7/1/0 días (o ya vencidos). Sin datos notables → no notifica.
 *
 * Lo invocan DOS disparos: la alarma exacta de [DailyStatusReceiver] (primario, atraviesa
 * Doze) y el trabajo periódico [DailyStatusWorker] (red de seguridad diferible). El dedupe
 * por día calendario evita el aviso doble cuando ambos coinciden.
 */
@Singleton
class DailyStatusNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleProfileDao: VehicleProfileDao,
    private val settings: DataStore<Preferences>,
    private val aiRulesSource: RestrictionRulesSource,
) {

    /**
     * @param force salta el dedupe por día — solo para el botón "Probar aviso" de Ajustes.
     * @return true si se posteó el aviso (o no había nada notable); false si falló algo.
     */
    suspend fun notifyIfNotable(
        nowMs: Long = System.currentTimeMillis(),
        trigger: String,
        force: Boolean = false,
    ): Boolean {
        val prefs = runCatching { settings.data.first() }.getOrNull() ?: return false
        if (!force && !DailyStatusSchedule.shouldNotify(prefs[PreferencesKeys.DAILY_STATUS_LAST_NOTIFIED_DAY], nowMs)) {
            Timber.i("DailyStatus($trigger): ya se notificó hoy, se omite")
            return true
        }

        val profiles = runCatching { vehicleProfileDao.observeAll().first() }.getOrNull() ?: return false
        val license = prefs[PreferencesKeys.LICENSE_EXPIRES_AT]
        val overrideRules = prefs[PreferencesKeys.PICO_PLACA_RULES_JSON]?.let(PicoYPlacaEngine::parseRulesJson)

        val lines = profiles.mapNotNull { notableLineFor(it, license, overrideRules, nowMs) }
        if (lines.isEmpty()) {
            Timber.i("DailyStatus($trigger): nada notable hoy")
            return true
        }

        postSummaryNotification(lines)
        markNotified(nowMs)
        Timber.i("DailyStatus($trigger): aviso posteado (${lines.size} vehículo/s)")
        return true
    }

    private suspend fun markNotified(nowMs: Long) {
        runCatching {
            settings.edit {
                it[PreferencesKeys.DAILY_STATUS_LAST_NOTIFIED_DAY] = DailyStatusSchedule.epochDayAt(nowMs)
            }
        }.onFailure { Timber.w(it, "DailyStatus: no se pudo guardar el día del último aviso") }
    }

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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle("Vehículo al día")
            .setContentText(lines.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }.onFailure { Timber.w(it, "DailyStatus: could not post summary") }
    }

    private fun buildContentIntent(): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_OPEN_AL_DIA, true)
            ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, NOTIFICATION_ID, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Documentos del vehículo", NotificationManager.IMPORTANCE_DEFAULT,
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_OPEN_AL_DIA = "open_al_dia"
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
