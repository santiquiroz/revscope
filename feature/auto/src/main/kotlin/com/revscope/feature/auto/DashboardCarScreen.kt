package com.revscope.feature.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

private const val REFRESH_INTERVAL_MS = 1_000L

/**
 * Live telemetry pane for Android Auto. Car App Library templates don't allow
 * free-form gauges outside navigation apps, so values render as rows refreshed
 * once per second — enough for temp/voltage/speed monitoring while driving.
 */
class DashboardCarScreen(
    carContext: CarContext,
    private val sessionManager: ObdSessionManager,
) : Screen(carContext) {

    private var readings: Map<String, ObdReading> = emptyMap()
    private var connection: ConnectionState = ConnectionState.Disconnected

    init {
        // Reactivo con conflate + delay: throttle natural a 1 Hz mientras fluyen datos y
        // CERO despertares cuando nada cambia — el poll anterior despertaba cada segundo
        // aunque el carro llevara horas desconectado.
        lifecycleScope.launch {
            combine(sessionManager.readings, sessionManager.connectionState) { r, c -> r to c }
                .distinctUntilChanged()
                .conflate()
                .collect { (newReadings, newConnection) ->
                    readings = newReadings
                    connection = newConnection
                    invalidate()
                    delay(REFRESH_INTERVAL_MS)
                }
        }
    }

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder().apply {
            addRow(valueRow(text(R.string.auto_speed), readings["0D"], "km/h"))
            addRow(valueRow(text(R.string.auto_rpm), readings["0C"], ""))
            addRow(valueRow(text(R.string.auto_coolant_temp), readings["05"], "°C"))
            addRow(valueRow(text(R.string.auto_battery), readings[ObdSessionManager.VBAT_PID], "V", decimals = 1))
            if (connection !is ConnectionState.Connected) {
                addAction(
                    Action.Builder()
                        .setTitle(text(R.string.auto_reconnect))
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener { sessionManager.reconnectToLast() }
                        .build()
                )
            }
        }.build()

        return PaneTemplate.Builder(pane)
            .setTitle(carContext.getString(R.string.auto_title, connectionLabel()))
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun valueRow(title: String, reading: ObdReading?, unit: String, decimals: Int = 0): Row {
        val value = when {
            reading == null -> "—"
            decimals > 0 -> "${LocaleNumbers.decimal(reading.value, decimals, locale())} $unit".trim()
            else -> "${reading.value.toInt()} $unit".trim()
        }
        return Row.Builder()
            .setTitle(title)
            .addText(value)
            .build()
    }

    private fun connectionLabel(): String = when (val s = connection) {
        is ConnectionState.Connected -> s.deviceName
        ConnectionState.Connecting -> text(R.string.auto_connecting)
        is ConnectionState.Error -> text(R.string.auto_no_connection)
        ConnectionState.Disconnected -> text(R.string.auto_disconnected)
    }

    private fun text(id: Int): String = carContext.getString(id)

    private fun locale(): Locale = carContext.resources.configuration.locales[0]
}
