package com.revscope.app.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object GearAnalyzer : Screen("gear")
    object Sensors : Screen("sensors")
    object Dtc : Screen("dtc")
    object Sessions : Screen("sessions")
    object VehicleProfile : Screen("vehicle")
    object Settings : Screen("settings")
    object AdapterScan : Screen("adapter_scan")
    object Mode22Scanner : Screen("mode22_scanner")
    object TrackMode : Screen("track_mode")
    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun withId(sessionId: Long) = "session_detail/$sessionId"
    }
}
