package com.revscope.core.obd.workshop

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

/**
 * Pure decision logic for the ECU odometer tamper check (PID 01 A6).
 *
 * "Rojo" en el plan se traduce a [DiagnosticRules.Nivel.FALLA] — el único nivel rojo
 * que expone DiagnosticRules.Nivel (OK/ATENCION/FALLA); no existe un nivel VENCIDO aquí.
 */
object OdometerVerifier {

    data class Reading(val epochMs: Long, val km: Double)

    /** Fracción mínima aceptable del delta ECU frente al delta recorrido por la app (80%). */
    private const val UMBRAL_AVANCE_MINIMO = 0.8
    private const val MAX_HISTORIAL = 50

    /** Ventana mínima de distancia app para que la comparación de desviación sea significativa. */
    private const val DISTANCIA_MINIMA_COMPARACION_KM = 5.0

    /**
     * Compara [nueva] contra la última lectura de [historial].
     * [distanciaAppKm] es la suma de distanceKm de las sesiones del perfil entre la
     * lectura anterior y [nueva] (0.0 si no hay lectura anterior).
     */
    fun evaluar(
        historial: List<Reading>,
        nueva: Reading,
        distanciaAppKm: Double,
        resolver: StringResolver,
    ): DiagnosticRules.Diagnosis {
        val anterior = historial.lastOrNull() ?: return lineaBase(nueva, resolver)
        return when {
            nueva.km < anterior.km -> retrocedio(anterior, nueva, resolver)
            distanciaAppKm < DISTANCIA_MINIMA_COMPARACION_KM -> ventanaInsuficiente(nueva, resolver)
            avanzaMenosQueApp(anterior, nueva, distanciaAppKm) -> avanceInsuficiente(anterior, nueva, distanciaAppKm, resolver)
            else -> normal(nueva, resolver)
        }
    }

    /** Agrega [nueva] al historial, capado a [MAX_HISTORIAL] entradas (descarta las más viejas). */
    fun agregarAlHistorial(historial: List<Reading>, nueva: Reading): List<Reading> =
        (historial + nueva).takeLast(MAX_HISTORIAL)

    private fun avanzaMenosQueApp(anterior: Reading, nueva: Reading, distanciaAppKm: Double): Boolean {
        val deltaEcu = nueva.km - anterior.km
        return deltaEcu < distanciaAppKm * UMBRAL_AVANCE_MINIMO
    }

    private fun km(value: Double, resolver: StringResolver): String = LocaleNumbers.decimal(value, 1, resolver.locale)

    private fun diagnosis(
        nivel: DiagnosticRules.Nivel,
        titulo: String,
        causa: String,
        resolver: StringResolver,
    ) = DiagnosticRules.Diagnosis(nivel, resolver.get(R.string.obd_odometer_area), titulo, causa)

    private fun lineaBase(nueva: Reading, resolver: StringResolver) = diagnosis(
        DiagnosticRules.Nivel.OK,
        resolver.get(R.string.obd_odometer_baseline, km(nueva.km, resolver)),
        resolver.get(R.string.obd_odometer_baseline_cause),
        resolver,
    )

    private fun retrocedio(anterior: Reading, nueva: Reading, resolver: StringResolver) = diagnosis(
        DiagnosticRules.Nivel.FALLA,
        resolver.get(R.string.obd_odometer_rolled_back, km(anterior.km, resolver), km(nueva.km, resolver)),
        resolver.get(R.string.obd_odometer_rolled_back_cause),
        resolver,
    )

    private fun avanceInsuficiente(
        anterior: Reading,
        nueva: Reading,
        distanciaAppKm: Double,
        resolver: StringResolver,
    ) = diagnosis(
        DiagnosticRules.Nivel.ATENCION,
        resolver.get(R.string.obd_odometer_behind_app, km(nueva.km - anterior.km, resolver), km(distanciaAppKm, resolver)),
        resolver.get(R.string.obd_odometer_behind_app_cause),
        resolver,
    )

    private fun ventanaInsuficiente(nueva: Reading, resolver: StringResolver) = diagnosis(
        DiagnosticRules.Nivel.OK,
        resolver.get(R.string.obd_odometer_reading, km(nueva.km, resolver)),
        resolver.get(R.string.obd_odometer_window_too_short),
        resolver,
    )

    private fun normal(nueva: Reading, resolver: StringResolver) = diagnosis(
        DiagnosticRules.Nivel.OK,
        resolver.get(R.string.obd_odometer_reading, km(nueva.km, resolver)),
        resolver.get(R.string.obd_odometer_consistent),
        resolver,
    )
}
