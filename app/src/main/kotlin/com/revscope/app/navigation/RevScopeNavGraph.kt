package com.revscope.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.revscope.feature.dashboard.DashboardScreen
import com.revscope.feature.dtc.DtcScreen
import com.revscope.feature.gear.GearAnalyzerScreen
import com.revscope.feature.sensors.SensorGraphScreen
import com.revscope.feature.session.SessionHistoryScreen
import com.revscope.feature.settings.SettingsScreen
import com.revscope.feature.vehicle.VehicleProfileScreen

@Composable
fun RevScopeNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen()
        }
        composable(Screen.GearAnalyzer.route) {
            GearAnalyzerScreen()
        }
        composable(Screen.Sensors.route) {
            SensorGraphScreen()
        }
        composable(Screen.Dtc.route) {
            DtcScreen()
        }
        composable(Screen.Sessions.route) {
            SessionHistoryScreen()
        }
        composable(Screen.VehicleProfile.route) {
            VehicleProfileScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
