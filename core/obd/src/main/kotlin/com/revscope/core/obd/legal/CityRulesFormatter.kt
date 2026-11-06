package com.revscope.core.obd.legal

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

/** Resumen de una línea de unas [PicoYPlacaEngine.CityRules] para notificaciones. */
object CityRulesFormatter {

    private val WEEKDAY_LABELS = listOf(
        2 to R.string.obd_city_rules_weekday_mon,
        3 to R.string.obd_city_rules_weekday_tue,
        4 to R.string.obd_city_rules_weekday_wed,
        5 to R.string.obd_city_rules_weekday_thu,
        6 to R.string.obd_city_rules_weekday_fri,
    )

    fun resumen(rules: PicoYPlacaEngine.CityRules, resolver: StringResolver): String {
        val rotacion = when (rules.scheme) {
            PicoYPlacaEngine.Scheme.WEEKDAY_ROTATION -> weekdayResumen(rules, resolver)
            PicoYPlacaEngine.Scheme.DATE_PARITY -> parityResumen(rules, resolver)
        }
        val horario = "${rules.startHour}-${rules.endHour}h"
        val motos = if (rules.motosExentas) resolver.get(R.string.obd_city_rules_motorcycles_exempt) else ""
        return "$rotacion · $horario$motos"
    }

    private fun weekdayResumen(rules: PicoYPlacaEngine.CityRules, resolver: StringResolver): String =
        WEEKDAY_LABELS
            .filter { (day, _) -> rules.rotation[day].orEmpty().isNotEmpty() }
            .joinToString(" ") { (day, label) ->
                "${resolver.get(label)}:${rules.rotation.getValue(day).joinToString(",")}"
            }

    private fun parityResumen(rules: PicoYPlacaEngine.CityRules, resolver: StringResolver): String {
        val impar = rules.dateParityRestricted[PicoYPlacaEngine.ODD_DAY_KEY].orEmpty().joinToString(",")
        val par = rules.dateParityRestricted[PicoYPlacaEngine.EVEN_DAY_KEY].orEmpty().joinToString(",")
        return resolver.get(R.string.obd_city_rules_parity, impar, par)
    }
}
