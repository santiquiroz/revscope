package com.revscope.feature.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.lifecycleScope
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Alert types worth interrupting the driver's screen for — safety/legal, not routine status. */
private val CAR_TOAST_ALERT_TYPES = setOf(
    AlertsEngine.AlertType.SPEED_CAMERA,
    AlertsEngine.AlertType.OVERHEAT,
    AlertsEngine.AlertType.LOW_VOLTAGE,
    AlertsEngine.AlertType.REDLINE,
    AlertsEngine.AlertType.MIL_ON,
    AlertsEngine.AlertType.PICO_Y_PLACA,
    AlertsEngine.AlertType.CUSTOM,
)

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

    @Inject
    lateinit var alertsEngine: AlertsEngine

    override fun createHostValidator(): HostValidator =
        // Personal/sideload build — accept any host (Android Auto, DHU)
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = RevScopeCarSession(sessionManager, alertsEngine)
}

class RevScopeCarSession(
    private val sessionManager: ObdSessionManager,
    private val alertsEngine: AlertsEngine,
) : Session() {

    // AlertsEngine's tone/TTS goes out on STREAM_MUSIC, which Android Auto reroutes to the
    // car speakers — if navigation/media audio is ducking or masking it, the driver never
    // hears it. CarToast renders on the cluster/head-unit screen itself, independent of
    // whatever is happening on the audio side, so it survives that failure mode.
    private var alertObserverStarted = false

    override fun onCreateScreen(intent: Intent): Screen {
        startAlertObserverOnce()
        return DashboardCarScreen(carContext, sessionManager)
    }

    private fun startAlertObserverOnce() {
        if (alertObserverStarted) return
        alertObserverStarted = true
        lifecycleScope.launch {
            alertsEngine.alerts
                .filter { it.type in CAR_TOAST_ALERT_TYPES }
                .collect { alert -> CarToast.makeText(carContext, alert.message, CarToast.LENGTH_LONG).show() }
        }
    }
}
