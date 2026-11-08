package com.revscope.core.obd.protocol

import androidx.annotation.StringRes
import com.revscope.core.obd.R

/**
 * Parses Mode 01 PID 01 — MIL status, DTC count and I/M readiness monitors.
 * SAE J1979: A = MIL(bit7) + count(bits0-6); B = continuous monitors
 * (bits0-2 supported, bit3 = compression ignition, bits4-6 incomplete);
 * C = non-continuous supported; D = non-continuous incomplete.
 */
object ReadinessParser {

    data class MonitorResult(@StringRes val nombre: Int, val soportado: Boolean, val completo: Boolean)

    data class ReadinessStatus(
        val milOn: Boolean,
        val dtcCount: Int,
        val isDiesel: Boolean,
        val monitors: List<MonitorResult>,
    )

    private val CONTINUOUS = listOf(
        R.string.obd_diagnostico_monitor_misfire,
        R.string.obd_diagnostico_monitor_fuel_system,
        R.string.obd_diagnostico_monitor_components,
    )

    private val SPARK_MONITORS = listOf(
        R.string.obd_diagnostico_monitor_catalyst,
        R.string.obd_diagnostico_monitor_heated_catalyst,
        R.string.obd_diagnostico_monitor_evap,
        R.string.obd_diagnostico_monitor_secondary_air,
        R.string.obd_diagnostico_monitor_ac_refrigerant,
        R.string.obd_diagnostico_monitor_o2_sensor,
        R.string.obd_diagnostico_monitor_o2_heater,
        R.string.obd_diagnostico_monitor_egr_vvt,
    )

    // null = bit reservado por SAE J1979 en motores diésel
    private val DIESEL_MONITORS = listOf(
        R.string.obd_diagnostico_monitor_nmhc_catalyst,
        R.string.obd_diagnostico_monitor_nox_catalyst,
        null,
        R.string.obd_diagnostico_monitor_boost_pressure,
        null,
        R.string.obd_diagnostico_monitor_exhaust_sensor,
        R.string.obd_diagnostico_monitor_pm_filter,
        R.string.obd_diagnostico_monitor_egr_vvt,
    )

    fun parse(raw: String): ReadinessStatus? {
        val bytes = ResponseParser.parsePidResponse(raw, "01") ?: return null
        if (bytes.size < 4) return null
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        val c = bytes[2].toInt() and 0xFF
        val d = bytes[3].toInt() and 0xFF

        val isDiesel = (b shr 3) and 0x01 == 1
        val monitors = buildList {
            CONTINUOUS.forEachIndexed { i, nombre ->
                add(MonitorResult(
                    nombre = nombre,
                    soportado = (b shr i) and 0x01 == 1,
                    completo = (b shr (i + 4)) and 0x01 == 0,
                ))
            }
            val names = if (isDiesel) DIESEL_MONITORS else SPARK_MONITORS
            names.forEachIndexed { i, nombre ->
                if (nombre == null) return@forEachIndexed
                add(MonitorResult(
                    nombre = nombre,
                    soportado = (c shr i) and 0x01 == 1,
                    completo = (d shr i) and 0x01 == 0,
                ))
            }
        }
        return ReadinessStatus(
            milOn = (a shr 7) and 0x01 == 1,
            dtcCount = a and 0x7F,
            isDiesel = isDiesel,
            monitors = monitors,
        )
    }
}
