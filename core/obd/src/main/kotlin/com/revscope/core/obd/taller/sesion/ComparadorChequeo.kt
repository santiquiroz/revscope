package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.BandasTipicas
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.workshop.MetricasChequeo
import kotlin.math.abs

enum class MetricaComparada {
    CODIGOS,
    AJUSTE_LARGO,
    CORRECCION_TOTAL,
    VOLTAJE,
    MOTOR,
    MONITORES,
    ;

    val etiqueta: String get() = when (this) { // i18n-ignore
        CODIGOS -> "Códigos" // i18n-ignore
        AJUSTE_LARGO -> "Ajuste largo" // i18n-ignore
        CORRECCION_TOTAL -> "Corrección total" // i18n-ignore
        VOLTAJE -> "Voltaje" // i18n-ignore
        MOTOR -> "Motor" // i18n-ignore
        MONITORES -> "Monitores" // i18n-ignore
    }

    fun etiqueta(resolver: StringResolver): String = resolver.get(
        when (this) {
            CODIGOS -> R.string.obd_session_metric_codes
            AJUSTE_LARGO -> R.string.obd_session_metric_long_trim
            CORRECCION_TOTAL -> R.string.obd_session_metric_total_correction
            VOLTAJE -> R.string.obd_session_metric_voltage
            MOTOR -> R.string.obd_session_metric_engine
            MONITORES -> R.string.obd_session_metric_monitors
        },
    )
}

data class FilaComparacion(
    val metrica: MetricaComparada,
    val base: String,
    val ahora: String,
    val cambio: String,
    val veredicto: Veredicto,
    val referencia: String? = null,
)

data class UmbralesComparacion(val ajuste: BandaReferencia, val voltaje: BandaReferencia) {
    companion object {
        private val TIPICAS = BandasTipicas.para(VehicleType.MOTORCYCLE)

        fun desde(bandas: Map<String, BandaReferencia>) = UmbralesComparacion(
            ajuste = bandas[ClavesBanda.CHEQUEO_AJUSTE_DELTA_PP] ?: TIPICAS.getValue(ClavesBanda.CHEQUEO_AJUSTE_DELTA_PP),
            voltaje = bandas[ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V] ?: TIPICAS.getValue(ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V),
        )
    }
}

// Una base es un solo chequeo: las diferencias numéricas piden atención como mucho, nunca se declaran falla.
object ComparadorChequeo {

    private const val VACIO = "—"
    private const val ECT_OPERACION_C = 60.0

    fun comparar(
        base: MetricasChequeo,
        ahora: MetricasChequeo,
        umbrales: UmbralesComparacion,
        resolver: StringResolver,
    ): List<FilaComparacion> =
        listOfNotNull(
            codigos(base, ahora, resolver),
            porcentaje(MetricaComparada.AJUSTE_LARGO, base.ltft, ahora.ltft, umbrales.ajuste, resolver),
            porcentaje(MetricaComparada.CORRECCION_TOTAL, base.correccionTotal, ahora.correccionTotal, umbrales.ajuste, resolver),
            voltaje(base, ahora, umbrales.voltaje, resolver),
            motor(base.ect, ahora.ect, resolver),
            monitores(base, ahora, resolver),
        )

    private fun codigos(base: MetricasChequeo, ahora: MetricasChequeo, resolver: StringResolver): FilaComparacion {
        if (!base.dtcsLeidos || !ahora.dtcsLeidos) {
            return FilaComparacion(
                MetricaComparada.CODIGOS, textoCodigos(base, resolver), textoCodigos(ahora, resolver),
                resolver.get(R.string.obd_session_scan_codes_unread), Veredicto.INFO,
            )
        }
        val nuevos = ahora.dtcs - base.dtcs.toSet()
        val (cambio, veredicto) = when {
            nuevos.isNotEmpty() -> resolver.get(R.string.obd_session_new_code, nuevos.joinToString()) to Veredicto.FALLA
            ahora.dtcs.isNotEmpty() -> resolver.get(R.string.obd_session_codes_remain, ahora.dtcs.joinToString()) to Veredicto.ATENCION
            base.dtcs.isNotEmpty() -> resolver.get(R.string.obd_session_codes_cleared, base.dtcs.joinToString()) to Veredicto.OK
            else -> resolver.get(R.string.obd_session_no_codes_both) to Veredicto.OK
        }
        return FilaComparacion(MetricaComparada.CODIGOS, textoCodigos(base, resolver), textoCodigos(ahora, resolver), cambio, veredicto)
    }

    private fun textoCodigos(m: MetricasChequeo, resolver: StringResolver): String = when {
        !m.dtcsLeidos -> resolver.get(R.string.obd_session_no_data)
        m.dtcs.isEmpty() -> resolver.get(R.string.obd_session_no_active_codes)
        else -> m.dtcs.joinToString()
    }

    private fun porcentaje(
        metrica: MetricaComparada,
        base: Double?,
        ahora: Double?,
        umbral: BandaReferencia,
        resolver: StringResolver,
    ): FilaComparacion? {
        if (base == null && ahora == null) return null
        val texto = { v: Double? -> v?.let { "${LocaleNumbers.signed(it, 1, resolver.locale)} %" } ?: VACIO }
        return numerica(metrica, base, ahora, umbral, texto, resolver) {
            resolver.get(R.string.obd_session_changed_percent, LocaleNumbers.signed(it, 1, resolver.locale))
        }
    }

    private fun voltaje(base: MetricasChequeo, ahora: MetricasChequeo, umbral: BandaReferencia, resolver: StringResolver): FilaComparacion? {
        if (base.voltaje == null && ahora.voltaje == null) return null
        val texto = { v: Double? -> v?.let { "${LocaleNumbers.decimal(it, 1, resolver.locale)} V" } ?: VACIO }
        if (estadosDistintos(base.motorEncendido, ahora.motorEncendido)) {
            return FilaComparacion(
                MetricaComparada.VOLTAJE, texto(base.voltaje), texto(ahora.voltaje),
                resolver.get(
                    R.string.obd_session_not_comparable_motor,
                    estadoMotor(base.motorEncendido, resolver),
                    estadoMotor(ahora.motorEncendido, resolver),
                ),
                Veredicto.INFO,
            )
        }
        return numerica(MetricaComparada.VOLTAJE, base.voltaje, ahora.voltaje, umbral, texto, resolver) {
            resolver.get(R.string.obd_session_changed_voltage, LocaleNumbers.signed(it, 1, resolver.locale))
        }
    }

    private fun numerica(
        metrica: MetricaComparada,
        base: Double?,
        ahora: Double?,
        umbral: BandaReferencia,
        texto: (Double?) -> String,
        resolver: StringResolver,
        textoCambio: (Double) -> String,
    ): FilaComparacion {
        val referencia = referencia(umbral, resolver)
        if (base == null || ahora == null) {
            return FilaComparacion(
                metrica, texto(base), texto(ahora), resolver.get(R.string.obd_session_no_comparison_data), Veredicto.INFO, referencia,
            )
        }
        val delta = FormatoTaller.redondear(ahora - base, 2)
        val limite = umbral.max ?: 0.0
        val (cambio, veredicto) =
            if (abs(delta) > limite) textoCambio(delta) to Veredicto.ATENCION
            else resolver.get(R.string.obd_session_no_relevant_change, LocaleNumbers.signed(delta, 1, resolver.locale)) to Veredicto.OK
        return FilaComparacion(metrica, texto(base), texto(ahora), cambio, veredicto, referencia)
    }

    private fun referencia(umbral: BandaReferencia, resolver: StringResolver): String {
        val limite = umbral.max ?: 0.0
        val decimals = if (limite % 1.0 == 0.0) 0 else 1
        val fuente = when (umbral.origen) {
            OrigenBanda.TIPICO -> resolver.get(R.string.obd_session_reference_typical)
            OrigenBanda.FUENTE -> resolver.get(R.string.obd_session_reference_source, umbral.fuente)
            OrigenBanda.USUARIO -> resolver.get(R.string.obd_session_reference_user)
        }
        return resolver.get(
            R.string.obd_session_reference,
            LocaleNumbers.decimal(limite, decimals, resolver.locale),
            umbral.unidad,
            fuente,
        )
    }

    private fun estadosDistintos(base: Boolean?, ahora: Boolean?): Boolean = base != null && ahora != null && base != ahora

    private fun estadoMotor(encendido: Boolean?, resolver: StringResolver): String =
        resolver.get(if (encendido == true) R.string.obd_session_engine_on else R.string.obd_session_engine_off)

    private fun motor(base: Double?, ahora: Double?, resolver: StringResolver): FilaComparacion? {
        if (base == null && ahora == null) return null
        val texto = { v: Double? -> v?.let { "${LocaleNumbers.decimal(it, 0, resolver.locale)} °C" } ?: VACIO }
        return FilaComparacion(MetricaComparada.MOTOR, texto(base), texto(ahora), contextoMotor(base, ahora, resolver), Veredicto.INFO)
    }

    private fun contextoMotor(base: Double?, ahora: Double?, resolver: StringResolver): String = when {
        base == null || ahora == null -> resolver.get(R.string.obd_session_motor_context_missing)
        esFrio(ahora) && !esFrio(base) -> resolver.get(R.string.obd_session_motor_context_now_cold)
        !esFrio(ahora) && esFrio(base) -> resolver.get(R.string.obd_session_motor_context_baseline_cold)
        else -> resolver.get(R.string.obd_session_motor_context_similar)
    }

    private fun esFrio(ect: Double): Boolean = ect < ECT_OPERACION_C

    private fun monitores(base: MetricasChequeo, ahora: MetricasChequeo, resolver: StringResolver): FilaComparacion? {
        if (base.monitoresTotales == null && ahora.monitoresTotales == null) return null
        val texto = { m: MetricasChequeo ->
            if (m.monitoresTotales == null) VACIO else resolver.get(
                R.string.obd_session_monitor_count,
                m.monitoresCompletos ?: 0,
                m.monitoresTotales,
            )
        }
        val completosBase = base.monitoresCompletos
        val completosAhora = ahora.monitoresCompletos
        val (cambio, veredicto) = when {
            completosBase == null || completosAhora == null -> resolver.get(R.string.obd_session_no_comparison_data) to Veredicto.INFO
            completosAhora < completosBase -> resolver.get(R.string.obd_session_fewer_monitors) to Veredicto.ATENCION
            else -> resolver.get(R.string.obd_session_no_relevant_change_plain) to Veredicto.OK
        }
        return FilaComparacion(MetricaComparada.MONITORES, texto(base), texto(ahora), cambio, veredicto)
    }
}
