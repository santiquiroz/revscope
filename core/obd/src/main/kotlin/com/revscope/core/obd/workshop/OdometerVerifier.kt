package com.revscope.core.obd.workshop

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

    /**
     * Compara [nueva] contra la última lectura de [historial].
     * [distanciaAppKm] es la suma de distanceKm de las sesiones del perfil entre la
     * lectura anterior y [nueva] (0.0 si no hay lectura anterior).
     */
    fun evaluar(
        historial: List<Reading>,
        nueva: Reading,
        distanciaAppKm: Double,
    ): DiagnosticRules.Diagnosis {
        val anterior = historial.lastOrNull() ?: return lineaBase(nueva)
        return when {
            nueva.km < anterior.km -> retrocedio(anterior, nueva)
            avanzaMenosQueApp(anterior, nueva, distanciaAppKm) -> avanceInsuficiente(anterior, nueva, distanciaAppKm)
            else -> normal(nueva)
        }
    }

    /** Agrega [nueva] al historial, capado a [MAX_HISTORIAL] entradas (descarta las más viejas). */
    fun agregarAlHistorial(historial: List<Reading>, nueva: Reading): List<Reading> =
        (historial + nueva).takeLast(MAX_HISTORIAL)

    private fun avanzaMenosQueApp(anterior: Reading, nueva: Reading, distanciaAppKm: Double): Boolean {
        val deltaEcu = nueva.km - anterior.km
        return deltaEcu < distanciaAppKm * UMBRAL_AVANCE_MINIMO
    }

    private fun lineaBase(nueva: Reading) = DiagnosticRules.Diagnosis(
        DiagnosticRules.Nivel.OK,
        "Odómetro",
        "Línea base registrada (%.1f km)".format(nueva.km),
        "Primera lectura del odómetro ECU — las próximas lecturas se comparan contra esta",
    )

    private fun retrocedio(anterior: Reading, nueva: Reading) = DiagnosticRules.Diagnosis(
        DiagnosticRules.Nivel.FALLA,
        "Odómetro",
        "El odómetro retrocedió (%.1f km → %.1f km)".format(anterior.km, nueva.km),
        "Posible manipulación del odómetro — el kilometraje no puede disminuir",
    )

    private fun avanceInsuficiente(anterior: Reading, nueva: Reading, distanciaAppKm: Double) = DiagnosticRules.Diagnosis(
        DiagnosticRules.Nivel.ATENCION,
        "Odómetro",
        "El odómetro avanzó %.1f km pero la app registró %.1f km recorridos".format(
            nueva.km - anterior.km,
            distanciaAppKm,
        ),
        "El odómetro avanza menos que la distancia registrada por la app — posible manipulación o lectura no confiable",
    )

    private fun normal(nueva: Reading) = DiagnosticRules.Diagnosis(
        DiagnosticRules.Nivel.OK,
        "Odómetro",
        "Odómetro %.1f km".format(nueva.km),
        "Lectura consistente con la distancia registrada por la app",
    )
}
