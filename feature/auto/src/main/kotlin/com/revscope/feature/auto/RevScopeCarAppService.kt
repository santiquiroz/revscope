package com.revscope.feature.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Android Auto entry point. Sideload-only (gauge apps aren't a Play-approved Auto
 * category) — enable "Unknown sources" in Android Auto developer settings.
 *
 * Reads the same [ObdSessionManager] the phone UI uses: if the phone is connected
 * to the adapter, the car screen shows live data with zero extra setup. The manager
 * also auto-connects on first injection, so opening the app in the car alone works.
 */
@AndroidEntryPoint
class RevScopeCarAppService : CarAppService() {

    @Inject
    lateinit var sessionManager: ObdSessionManager

    override fun createHostValidator(): HostValidator =
        // Personal/sideload build — accept any host (Android Auto, DHU)
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = RevScopeCarSession(sessionManager)
}

class RevScopeCarSession(
    private val sessionManager: ObdSessionManager,
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        DashboardCarScreen(carContext, sessionManager)
}
