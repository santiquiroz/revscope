package com.revscope.app.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object GearAnalyzer : Screen("gear")
    object Sensors : Screen("sensors")
    object Dtc : Screen("dtc")
    object Sessions : Screen("sessions")
    object VehicleProfile : Screen("vehicle/{profileId}") {
        fun createRoute(id: Long) = "vehicle/$id"
    }
    object Settings : Screen("settings")
    object AdapterScan : Screen("adapter_scan")
}
