package com.revscope.core.obd.legal

/**
 * Reglas de restricción vehicular para la posición actual cuando [CityRegistry] no cubre
 * la ciudad o sus reglas están vencidas. Definida en :core:obd e implementada en
 * :core:intelligence (IA + cache) — mismo patrón que GpsInfoSink/CityInfoAlerter para
 * evitar el ciclo de dependencias entre módulos; el binding vive en el módulo :app.
 */
interface RestrictionRulesSource {

    /** Reglas vigentes para la ciudad del fix, o null (sin datos / sin restricción / gate apagado). */
    suspend fun rulesFor(latitude: Double, longitude: Double): PicoYPlacaEngine.CityRules?

    /** Reglas para una ciudad del [CityRegistry] por id (perfil), sin GPS — misma cache que [rulesFor]. */
    suspend fun rulesForCity(cityId: String): PicoYPlacaEngine.CityRules?
}
