package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import kotlin.math.pow

enum class OrigenBarometrica(val nombre: String, val nota: String, val enFrase: String) {
    ECU("PID 33 de la ECU", "resolución del PID", "el PID 33 de la ECU"),
    TELEFONO("Barómetro del teléfono", "típico", "el barómetro del teléfono"),
    ALTITUD_GPS("Presión estimada por la altitud GPS", "por clima y altitud GPS", "la presión estimada por la altitud GPS"),
}

data class ReferenciaBarometrica(val kPa: Double, val origen: OrigenBarometrica, val incertidumbreKpa: Double) {
    val etiqueta: String get() = "${origen.nombre} ${margen()}"

    val enFrase: String get() = "${origen.enFrase} ${margen()}"

    private fun margen() = "(±${FormatoTaller.compacto(incertidumbreKpa)} kPa, ${origen.nota})"
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
