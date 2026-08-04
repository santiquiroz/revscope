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
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
            addRow(valueRow("Velocidad", readings["0D"], "km/h"))
            addRow(valueRow("RPM", readings["0C"], ""))
            addRow(valueRow("Temp. motor", readings["05"], "°C"))
            addRow(valueRow("Batería", readings[ObdSessionManager.VBAT_PID], "V", decimals = 1))
            if (connection !is ConnectionState.Connected) {
                addAction(
                    Action.Builder()
                        .setTitle("Reconectar")
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener { sessionManager.reconnectToLast() }
                        .build()
                )
            }
        }.build()

        return PaneTemplate.Builder(pane)
            .setTitle("RevScope — ${connectionLabel()}")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun valueRow(title: String, reading: ObdReading?, unit: String, decimals: Int = 0): Row {
        val text = when {
            reading == null -> "—"
            decimals > 0 -> "%.${decimals}f $unit".trim().format(reading.value)
            else -> "${reading.value.toInt()} $unit".trim()
        }
        return Row.Builder()
            .setTitle(title)
            .addText(text)
            .build()
    }

    private fun connectionLabel(): String = when (val s = connection) {
        is ConnectionState.Connected -> s.deviceName
        ConnectionState.Connecting -> "Conectando…"
        is ConnectionState.Error -> "Sin conexión"
        ConnectionState.Disconnected -> "Desconectado"
    }
}
