package com.revscope.core.obd.legal

import com.revscope.core.data.db.entities.VehicleProfileEntity
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
    ): List<DocStatus> = listOf(
        expiryStatus(DocType.SOAT, "SOAT", documents.soatExpiresAt, nowMs, timeZoneId),
        expiryStatus(DocType.RTM, "Tecnomecánica", documents.rtmExpiresAt, nowMs, timeZoneId),
        picoYPlacaStatus(documents, overrideRules, aiFallbackRules, nowMs),
        expiryStatus(DocType.TODO_RIESGO, "Todo riesgo", documents.insuranceExpiresAt, nowMs, timeZoneId),
        expiryStatus(DocType.LICENCIA, "Licencia", documents.licenseExpiresAt, nowMs, timeZoneId),
    )

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
    fun bannerText(statuses: List<DocStatus>): String? {
        val vencidosNoPico = statuses.filter { it.nivel == Nivel.VENCIDO && it.tipo != DocType.PICO_Y_PLACA }
        val picoYPlaca = statuses.firstOrNull { it.tipo == DocType.PICO_Y_PLACA }
        val picoYPlacaAplicaHoy = picoYPlaca?.nivel == Nivel.VENCIDO || picoYPlaca?.nivel == Nivel.ATENCION
        if (vencidosNoPico.isEmpty() && !picoYPlacaAplicaHoy) return null
        val partes = buildList {
            vencidosNoPico.forEach { add("${it.titulo} vencido") }
            if (picoYPlacaAplicaHoy) add(picoYPlacaBannerPhrase(picoYPlaca))
        }
        return partes.joinToString(" · ")
    }

    private fun picoYPlacaBannerPhrase(status: DocStatus?): String =
        status?.horaLimite?.let { "Pico y placa hasta las $it:00" } ?: "Pico y placa restringido hoy"

    private fun expiryStatus(
        tipo: DocType,
        titulo: String,
        expiresAt: Long?,
        nowMs: Long,
        timeZoneId: String,
    ): DocStatus {
        if (expiresAt == null) return DocStatus(tipo, Nivel.SIN_CONFIGURAR, titulo, "Por configurar")
        val daysUntil = daysBetween(nowMs, expiresAt, timeZoneId)
        return when {
            daysUntil < 0 -> DocStatus(tipo, Nivel.VENCIDO, titulo, vencidoHaceDiasTexto(daysUntil), diasRestantes = daysUntil)
            daysUntil == 0L -> DocStatus(tipo, Nivel.ATENCION, titulo, "Vence hoy", diasRestantes = daysUntil)
            daysUntil <= WARNING_THRESHOLD_DAYS ->
                DocStatus(tipo, Nivel.ATENCION, titulo, "Vence en $daysUntil días", diasRestantes = daysUntil)
            else -> DocStatus(tipo, Nivel.OK, titulo, "Vence en $daysUntil días", diasRestantes = daysUntil)
        }
    }

    private fun vencidoHaceDiasTexto(daysUntil: Long): String {
        val diasVencido = -daysUntil
        return if (diasVencido == 1L) "Venció hace 1 día" else "Venció hace $diasVencido días"
    }

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
    ): DocStatus {
        val titulo = "Pico y placa"
        val plate = documents.plate?.trim()
        val cityId = documents.picoPlacaCity
        if (cityId == null || plate.isNullOrEmpty()) {
            return DocStatus(DocType.PICO_Y_PLACA, Nivel.SIN_CONFIGURAR, titulo, "Por configurar")
        }
        val rules = pickActiveRules(cityId, overrideRules, aiFallbackRules, nowMs)
            ?: return DocStatus(DocType.PICO_Y_PLACA, Nivel.SIN_CONFIGURAR, titulo, rulesPendientesDetalle(cityId))
        val result = PicoYPlacaEngine.check(plate, documents.isMotorcycle, rules, nowMs)
        return when (result.status) {
            PicoYPlacaEngine.Status.SIN_RESTRICCION ->
                DocStatus(DocType.PICO_Y_PLACA, Nivel.OK, titulo, sinRestriccionDetalle(documents, rules))
            PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO ->
                DocStatus(DocType.PICO_Y_PLACA, Nivel.ATENCION, titulo, "No salgas hasta las ${result.endHour}:00", result.endHour)
            PicoYPlacaEngine.Status.RESTRINGIDO_AHORA ->
                DocStatus(DocType.PICO_Y_PLACA, Nivel.VENCIDO, titulo, "Hoy tienes restricción hasta las ${result.endHour}:00", result.endHour)
            PicoYPlacaEngine.Status.REGLAS_VENCIDAS ->
                DocStatus(DocType.PICO_Y_PLACA, Nivel.SIN_CONFIGURAR, titulo, "Actualiza las reglas del semestre")
            PicoYPlacaEngine.Status.SIN_DATOS ->
                DocStatus(DocType.PICO_Y_PLACA, Nivel.SIN_CONFIGURAR, titulo, "Por configurar")
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

    private fun sinRestriccionDetalle(documents: VehicleDocuments, rules: PicoYPlacaEngine.CityRules): String =
        if (documents.isMotorcycle && rules.motosExentas) "Motos exentas en ${rules.displayName}" else "Puedes salir"

    private fun rulesPendientesDetalle(cityId: String): String {
        val nombre = CityRegistry.CITIES.firstOrNull { it.id == cityId }?.nombre ?: cityId
        return "Configura la rotación vigente de $nombre en Ajustes (JSON)"
    }
}
