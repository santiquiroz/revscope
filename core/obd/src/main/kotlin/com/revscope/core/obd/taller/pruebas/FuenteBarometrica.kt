package com.revscope.core.obd.taller.pruebas

import androidx.annotation.StringRes
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import kotlin.math.pow

enum class OrigenBarometrica(
    @StringRes val nombreRes: Int,
    @StringRes val notaRes: Int,
    @StringRes val enFraseRes: Int,
) {
    ECU(R.string.obd_baro_ecu_nombre, R.string.obd_baro_ecu_nota, R.string.obd_baro_ecu_en_frase),
    TELEFONO(R.string.obd_baro_telefono_nombre, R.string.obd_baro_telefono_nota, R.string.obd_baro_telefono_en_frase),
    ALTITUD_GPS(R.string.obd_baro_altitud_nombre, R.string.obd_baro_altitud_nota, R.string.obd_baro_altitud_en_frase),
    ;

    fun nombre(resolver: StringResolver): String = resolver.get(nombreRes)

    fun enFrase(resolver: StringResolver): String = resolver.get(enFraseRes)
}

// «Barómetro del teléfono (±0,5 kPa, típico)»: la fuente siempre con su margen y la nota de su origen.
data class ReferenciaBarometrica(val kPa: Double, val origen: OrigenBarometrica, val incertidumbreKpa: Double) {
    fun etiqueta(resolver: StringResolver): String = conMargen(origen.nombreRes, resolver)

    fun enFrase(resolver: StringResolver): String = conMargen(origen.enFraseRes, resolver)

    private fun conMargen(@StringRes fuente: Int, resolver: StringResolver): String = resolver.get(
        R.string.obd_baro_con_margen,
        resolver.get(fuente),
        LocaleNumbers.compact(incertidumbreKpa, resolver.locale),
        resolver.get(origen.notaRes),
    )
}

// Lo que el teléfono sabe del ambiente cuando termina la prueba: su barómetro (hPa) y la última altitud GPS.
data class LecturasAmbiente(val presionTelefonoHpa: Double? = null, val altitudGpsM: Double? = null)

// La presión barométrica de referencia, de la primera fuente disponible: PID 33 de la ECU, barómetro del
// teléfono o la altitud GPS con la atmósfera estándar (esta última es una estimación y así se etiqueta).
object FuenteBarometrica {

    const val NIVEL_DEL_MAR_KPA = 101.325
    const val INCERTIDUMBRE_ECU_KPA = 1.0
    const val INCERTIDUMBRE_TELEFONO_KPA = 0.5
    const val INCERTIDUMBRE_ALTITUD_KPA = 3.0

    private const val GRADIENTE = 2.25577e-5
    private const val EXPONENTE = 5.25588
    private const val HPA_POR_KPA = 10.0
    private val KPA_PLAUSIBLE = 50.0..110.0
    private val ALTITUD_PLAUSIBLE_M = -500.0..6_000.0

    fun elegir(ecuKpa: Double?, ambiente: LecturasAmbiente?): ReferenciaBarometrica? = disponibles(ecuKpa, ambiente).firstOrNull()

    fun disponibles(ecuKpa: Double?, ambiente: LecturasAmbiente?): List<ReferenciaBarometrica> = listOfNotNull(
        desdeEcu(ecuKpa),
        desdeTelefono(ambiente?.presionTelefonoHpa),
        desdeAltitud(ambiente?.altitudGpsM),
    )

    fun presionPorAltitud(altitudM: Double): Double = NIVEL_DEL_MAR_KPA * (1 - GRADIENTE * altitudM).pow(EXPONENTE)

    fun altitudPorPresion(kPa: Double): Double = (1 - (kPa / NIVEL_DEL_MAR_KPA).pow(1 / EXPONENTE)) / GRADIENTE

    private fun desdeEcu(kPa: Double?): ReferenciaBarometrica? =
        kPa?.takeIf { it in KPA_PLAUSIBLE }?.let { ReferenciaBarometrica(it, OrigenBarometrica.ECU, INCERTIDUMBRE_ECU_KPA) }

    private fun desdeTelefono(hPa: Double?): ReferenciaBarometrica? = hPa?.div(HPA_POR_KPA)?.takeIf { it in KPA_PLAUSIBLE }
        ?.let { ReferenciaBarometrica(it, OrigenBarometrica.TELEFONO, INCERTIDUMBRE_TELEFONO_KPA) }

    private fun desdeAltitud(altitudM: Double?): ReferenciaBarometrica? = altitudM?.takeIf { it in ALTITUD_PLAUSIBLE_M }
        ?.let { ReferenciaBarometrica(presionPorAltitud(it), OrigenBarometrica.ALTITUD_GPS, INCERTIDUMBRE_ALTITUD_KPA) }
}
