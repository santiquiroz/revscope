package com.revscope.core.obd.legal

/** Resumen de una línea de unas [PicoYPlacaEngine.CityRules] para notificaciones. */
object CityRulesFormatter {

    private val WEEKDAY_LABELS = listOf(2 to "L", 3 to "M", 4 to "X", 5 to "J", 6 to "V")

    fun resumen(rules: PicoYPlacaEngine.CityRules): String {
        val rotacion = when (rules.scheme) {
            PicoYPlacaEngine.Scheme.WEEKDAY_ROTATION -> weekdayResumen(rules)
            PicoYPlacaEngine.Scheme.DATE_PARITY -> parityResumen(rules)
        }
        val horario = "${rules.startHour}-${rules.endHour}h"
        val motos = if (rules.motosExentas) " · motos exentas" else ""
        return "$rotacion · $horario$motos"
    }

    private fun weekdayResumen(rules: PicoYPlacaEngine.CityRules): String =
        WEEKDAY_LABELS
            .filter { (day, _) -> rules.rotation[day].orEmpty().isNotEmpty() }
            .joinToString(" ") { (day, label) -> "$label:${rules.rotation.getValue(day).joinToString(",")}" }

    private fun parityResumen(rules: PicoYPlacaEngine.CityRules): String {
        val impar = rules.dateParityRestricted[PicoYPlacaEngine.ODD_DAY_KEY].orEmpty().joinToString(",")
        val par = rules.dateParityRestricted[PicoYPlacaEngine.EVEN_DAY_KEY].orEmpty().joinToString(",")
        return "Impar:$impar Par:$par"
    }
}
