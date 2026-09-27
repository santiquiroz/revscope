package com.revscope.app.navigation

sealed class Screen(val route: String) {
    /** Ruta real a navegar cuando no hay argumentos que completar — por defecto igual a
     * [route], pero una screen con args opcionales (ver [Settings]) la sobreescribe porque
     * [route] ahí es el patrón `{arg}` que solo NavHost debe usar para registrar el destino. */
    open val navRoute: String get() = route
    object Onboarding : Screen("onboarding")
    object AiValue : Screen("ai_value")
    object Dashboard : Screen("dashboard")
    object Workshop : Screen("workshop")
    object TallerNuevaSesion : Screen("taller_nueva_sesion")
    object TallerSesion : Screen("taller_sesion/{sesionId}") {
        fun withId(sesionId: Long) = "taller_sesion/$sesionId"
    }
    object PruebaGuiada : Screen("prueba_guiada?tipo={tipo}") {
        override val navRoute: String = "prueba_guiada"

        fun withTipo(tipo: String): String = "prueba_guiada?tipo=$tipo"
    }
    object Multimetro : Screen("multimetro?sensor={sensor}") {
        override val navRoute: String = "multimetro"

        fun withSensor(sensor: String): String = "multimetro?sensor=$sensor"
    }
    object ConocimientoModelo : Screen("conocimiento_modelo")
    object ReferenciasModelo : Screen("referencias_modelo")
    object AlDia : Screen("al_dia")
    object HealthCheck : Screen("health_check")
    object LiveMixture : Screen("live_mixture")
    object O2Wave : Screen("o2_wave")
    object Mode06 : Screen("mode06")
    object LiveMap : Screen("map")
    object GearAnalyzer : Screen("gear")
    object Sensors : Screen("sensors")
    object Dtc : Screen("dtc")
    object Sessions : Screen("sessions")
    object VehicleProfile : Screen("vehicle")
    object Maintenance : Screen("maintenance")
    object Odometer : Screen("odometer")
    object SpeedComparison : Screen("speed_comparison")
    object MechanicChat : Screen("mechanic_chat")
    object Settings : Screen("settings?expand={expand}") {
        const val ARG_EXPAND = "expand"
        override val navRoute: String = "settings"

        /** [section] es el nombre (case-insensitive) de un SettingsSectionId, ej. "mapa". */
        fun withExpand(section: String): String = "settings?expand=$section"
    }
    object AdapterScan : Screen("adapter_scan")
    object Mode22Scanner : Screen("mode22_scanner")
    object TrackMode : Screen("track_mode")
    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun withId(sessionId: Long) = "session_detail/$sessionId"
    }
    object SessionCompare : Screen("session_compare/{sessionA}/{sessionB}") {
        fun withIds(a: Long, b: Long) = "session_compare/$a/$b"
    }
}
