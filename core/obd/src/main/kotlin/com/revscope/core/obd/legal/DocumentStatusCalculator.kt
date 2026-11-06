package com.revscope.core.obd.legal

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Motor puro que combina fechas de vencimiento del perfil activo y el estado de
 * [PicoYPlacaEngine] en una lista de semáforos "vehículo al día". Sin dependencias de
 * Android ni efectos secundarios — se comparte entre la pantalla AlDia y el banner del
 * Dashboard para no duplicar el cálculo.
 */
object DocumentStatusCalculator {

    enum class Nivel { OK, ATENCION, VENCIDO, SIN_CONFIGURAR }

    enum class DocType { SOAT, RTM, PICO_Y_PLACA, TODO_RIESGO, LICENCIA }

    data class DocStatus(
        val tipo: DocType,
        val nivel: Nivel,
        val titulo: String,
        val detalle: String,
        /** Hora límite de la restricción de pico y placa (0-23); null para los demás tipos. */
        val horaLimite: Int? = null,
        /** Días hasta el vencimiento (negativo si ya venció); null para pico y placa y sin configurar. */
        val diasRestantes: Long? = null,
    )

    data class VehicleDocuments(
        val plate: String?,
        val picoPlacaCity: String?,
        val isMotorcycle: Boolean,
        val soatExpiresAt: Long?,
        val rtmExpiresAt: Long?,
        val insuranceExpiresAt: Long?,
        val licenseExpiresAt: Long?,
    )

    private const val WARNING_THRESHOLD_DAYS = 30L

    /**
     * [overrideRules] es la edición manual del usuario (Ajustes → JSON de reglas). Solo se usa
     * cuando su `cityId` coincide con `documents.picoPlacaCity`; de lo contrario se resuelven
     * las reglas vigentes de esa ciudad desde [CityRegistry]. [aiFallbackRules] (opcional,
     * obtenidas vía RestrictionRulesSource cuando [needsAiFallback]) solo aplican si las
     * curadas faltan o están vencidas.
     */
    fun calculate(
        documents: VehicleDocuments,
        overrideRules: PicoYPlacaEngine.CityRules?,
        nowMs: Long,
        timeZoneId: String = "America/Bogota",
        aiFallbackRules: PicoYPlacaEngine.CityRules? = null,
        resolver: StringResolver,
    ): List<DocStatus> {
        val expiry = ExpiryContext(nowMs, timeZoneId, resolver)
        return listOf(
            expiryStatus(DocType.SOAT, R.string.obd_doc_title_soat, documents.soatExpiresAt, expiry),
            expiryStatus(DocType.RTM, R.string.obd_doc_title_rtm, documents.rtmExpiresAt, expiry),
            picoYPlacaStatus(documents, overrideRules, aiFallbackRules, nowMs, resolver),
            expiryStatus(DocType.TODO_RIESGO, R.string.obd_doc_title_comprehensive, documents.insuranceExpiresAt, expiry),
            expiryStatus(DocType.LICENCIA, R.string.obd_doc_title_license, documents.licenseExpiresAt, expiry),
        )
    }

    private class ExpiryContext(val nowMs: Long, val timeZoneId: String, val resolver: StringResolver)

    /** True cuando la ciudad del perfil no tiene reglas curadas vigentes — momento de preguntar a la IA. */
    fun needsAiFallback(cityId: String?, overrideRules: PicoYPlacaEngine.CityRules?, nowMs: Long): Boolean {
        if (cityId == null) return false
        val curated = CityRegistry.resolveRules(cityId, overrideRules) ?: return true
        return nowMs !in curated.validFromMs..curated.validUntilMs
    }

    /** Convierte un perfil de vehículo + fecha de licencia (DataStore) en [VehicleDocuments]. */
    fun fromProfile(profile: VehicleProfileEntity, licenseExpiresAt: Long?): VehicleDocuments =
        VehicleDocuments(
            plate = profile.plate,
            picoPlacaCity = profile.picoPlacaCity,
            isMotorcycle = profile.type == "MOTORCYCLE",
            soatExpiresAt = profile.soatExpiresAt,
            rtmExpiresAt = profile.rtmExpiresAt,
            insuranceExpiresAt = profile.insuranceExpiresAt,
            licenseExpiresAt = licenseExpiresAt,
        )

    /** Texto corto para el banner del Dashboard; null si no hay nada urgente hoy. */
    fun bannerText(statuses: List<DocStatus>, resolver: StringResolver): String? {
        val vencidosNoPico = statuses.filter { it.nivel == Nivel.VENCIDO && it.tipo != DocType.PICO_Y_PLACA }
        val picoYPlaca = statuses.firstOrNull { it.tipo == DocType.PICO_Y_PLACA }
        val picoYPlacaAplicaHoy = picoYPlaca?.nivel == Nivel.VENCIDO || picoYPlaca?.nivel == Nivel.ATENCION
        if (vencidosNoPico.isEmpty() && !picoYPlacaAplicaHoy) return null
        val partes = buildList {
            vencidosNoPico.forEach { add(resolver.get(R.string.obd_doc_banner_expired, it.titulo)) }
            if (picoYPlacaAplicaHoy) add(picoYPlacaBannerPhrase(picoYPlaca, resolver))
        }
        return partes.joinToString(" · ")
    }

    private fun picoYPlacaBannerPhrase(status: DocStatus?, resolver: StringResolver): String =
        status?.horaLimite?.let { resolver.get(R.string.obd_doc_banner_pico_placa_until, it) }
            ?: resolver.get(R.string.obd_doc_banner_pico_placa_today)

    private fun expiryStatus(tipo: DocType, tituloRes: Int, expiresAt: Long?, ctx: ExpiryContext): DocStatus {
        val resolver = ctx.resolver
        val titulo = resolver.get(tituloRes)
        if (expiresAt == null) {
            return DocStatus(tipo, Nivel.SIN_CONFIGURAR, titulo, resolver.get(R.string.obd_doc_not_configured))
        }
        val daysUntil = daysBetween(ctx.nowMs, expiresAt, ctx.timeZoneId)
        return when {
            daysUntil < 0 ->
                DocStatus(tipo, Nivel.VENCIDO, titulo, vencidoHaceDiasTexto(daysUntil, resolver), diasRestantes = daysUntil)
            daysUntil == 0L ->
                DocStatus(tipo, Nivel.ATENCION, titulo, resolver.get(R.string.obd_doc_expires_today), diasRestantes = daysUntil)
            daysUntil <= WARNING_THRESHOLD_DAYS ->
                DocStatus(tipo, Nivel.ATENCION, titulo, venceEnDiasTexto(daysUntil, resolver), diasRestantes = daysUntil)
            else -> DocStatus(tipo, Nivel.OK, titulo, venceEnDiasTexto(daysUntil, resolver), diasRestantes = daysUntil)
        }
    }

    private fun venceEnDiasTexto(daysUntil: Long, resolver: StringResolver): String =
        resolver.plural(R.plurals.obd_doc_expires_in_days, daysUntil.toPluralCount(), daysUntil)

    private fun vencidoHaceDiasTexto(daysUntil: Long, resolver: StringResolver): String {
        val diasVencido = -daysUntil
        return resolver.plural(R.plurals.obd_doc_expired_days_ago, diasVencido.toPluralCount(), diasVencido)
    }

    private fun Long.toPluralCount(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    private fun daysBetween(nowMs: Long, expiresAtMs: Long, timeZoneId: String): Long {
        val nowDate = Instant.ofEpochMilli(nowMs).atZone(ZoneId.of(timeZoneId)).toLocalDate()
        val expiryDate = Instant.ofEpochMilli(expiresAtMs).atZone(ZoneOffset.UTC).toLocalDate()
        return ChronoUnit.DAYS.between(nowDate, expiryDate)
    }

    private fun picoYPlacaStatus(
        documents: VehicleDocuments,
        overrideRules: PicoYPlacaEngine.CityRules?,
        aiFallbackRules: PicoYPlacaEngine.CityRules?,
        nowMs: Long,
        resolver: StringResolver,
    ): DocStatus {
        val titulo = resolver.get(R.string.obd_doc_title_pico_placa)
        val sinConfigurar = DocStatus(DocType.PICO_Y_PLACA, Nivel.SIN_CONFIGURAR, titulo, resolver.get(R.string.obd_doc_not_configured))
        val plate = documents.plate?.trim()
        val cityId = documents.picoPlacaCity
        if (cityId == null || plate.isNullOrEmpty()) return sinConfigurar
        val rules = pickActiveRules(cityId, overrideRules, aiFallbackRules, nowMs)
            ?: return sinConfigurar.copy(detalle = rulesPendientesDetalle(cityId, resolver))
        val result = PicoYPlacaEngine.check(plate, documents.isMotorcycle, rules, nowMs)
        return when (result.status) {
            PicoYPlacaEngine.Status.SIN_RESTRICCION ->
                DocStatus(DocType.PICO_Y_PLACA, Nivel.OK, titulo, sinRestriccionDetalle(documents, rules, resolver))
            PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO -> DocStatus(
                DocType.PICO_Y_PLACA, Nivel.ATENCION, titulo,
                resolver.get(R.string.obd_doc_pico_placa_wait_until, result.endHour.toString()), result.endHour,
            )
            PicoYPlacaEngine.Status.RESTRINGIDO_AHORA -> DocStatus(
                DocType.PICO_Y_PLACA, Nivel.VENCIDO, titulo,
                resolver.get(R.string.obd_doc_pico_placa_restricted_until, result.endHour.toString()), result.endHour,
            )
            PicoYPlacaEngine.Status.REGLAS_VENCIDAS ->
                sinConfigurar.copy(detalle = resolver.get(R.string.obd_doc_pico_placa_update_rules))
            PicoYPlacaEngine.Status.SIN_DATOS -> sinConfigurar
        }
    }

    /** Curadas vigentes > IA vigente > curadas vencidas (para el status REGLAS_VENCIDAS) > null. */
    private fun pickActiveRules(
        cityId: String,
        overrideRules: PicoYPlacaEngine.CityRules?,
        aiFallbackRules: PicoYPlacaEngine.CityRules?,
        nowMs: Long,
    ): PicoYPlacaEngine.CityRules? {
        val curated = CityRegistry.resolveRules(cityId, overrideRules)
        return when {
            curated != null && nowMs in curated.validFromMs..curated.validUntilMs -> curated
            aiFallbackRules != null && nowMs in aiFallbackRules.validFromMs..aiFallbackRules.validUntilMs -> aiFallbackRules
            else -> curated
        }
    }

    private fun sinRestriccionDetalle(
        documents: VehicleDocuments,
        rules: PicoYPlacaEngine.CityRules,
        resolver: StringResolver,
    ): String =
        if (documents.isMotorcycle && rules.motosExentas) {
            resolver.get(R.string.obd_doc_pico_placa_motorcycles_exempt, rules.displayName)
        } else {
            resolver.get(R.string.obd_doc_pico_placa_free)
        }

    private fun rulesPendientesDetalle(cityId: String, resolver: StringResolver): String {
        val nombre = CityRegistry.CITIES.firstOrNull { it.id == cityId }?.nombre ?: cityId
        return resolver.get(R.string.obd_doc_pico_placa_rules_pending, nombre)
    }
}
