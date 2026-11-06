package com.revscope.feature.vehicle.perfilcan

import androidx.annotation.StringRes
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.perfilcan.ClavesSenal
import com.revscope.core.obd.perfilcan.FuenteSenal
import com.revscope.core.obd.perfilcan.ModoFuente
import com.revscope.core.obd.perfilcan.SenalCan
import com.revscope.feature.vehicle.R

internal object TextosBusCan {

    @StringRes
    fun modo(modo: ModoFuente): Int = when (modo) {
        ModoFuente.ESTANDAR -> R.string.vehicle_can_standard
        ModoFuente.COMBINADO -> R.string.vehicle_can_combined
        ModoFuente.SOLO_BUS -> R.string.vehicle_can_bus_only
    }

    @StringRes
    fun descripcionModo(modo: ModoFuente): Int = when (modo) {
        ModoFuente.ESTANDAR -> R.string.vehicle_can_standard_description
        ModoFuente.COMBINADO -> R.string.vehicle_can_combined_description
        ModoFuente.SOLO_BUS -> R.string.vehicle_can_bus_only_description
    }

    @StringRes
    fun fuente(fuente: FuenteSenal): Int = when (fuente) {
        FuenteSenal.AUTO -> R.string.vehicle_can_source_automatic
        FuenteSenal.PID -> R.string.vehicle_can_source_obd
        FuenteSenal.BUS -> R.string.vehicle_can_source_bus
        FuenteSenal.GPS -> R.string.vehicle_can_source_gps
        FuenteSenal.CALCULADA -> R.string.vehicle_can_source_calculated
    }

    fun opciones(clave: String): List<FuenteSenal> = buildList {
        add(FuenteSenal.AUTO)
        if (clave in clavesPid) add(FuenteSenal.PID)
        add(FuenteSenal.BUS)
        if (clave == ClavesSenal.VELOCIDAD) add(FuenteSenal.GPS)
        if (clave == ClavesSenal.GEAR) add(FuenteSenal.CALCULADA)
    }

    fun instruccion(clave: String, resolver: StringResolver): String = resolver.get(
        when (clave) {
            ClavesSenal.GEAR -> R.string.vehicle_can_test_gear
            ClavesSenal.PATA_LATERAL -> R.string.vehicle_can_test_side_stand
            ClavesSenal.EMBRAGUE -> R.string.vehicle_can_test_clutch
            ClavesSenal.KILL_SWITCH -> R.string.vehicle_can_test_kill_switch
            ClavesSenal.MOTOR_EN_MARCHA -> R.string.vehicle_can_test_engine_running
            ClavesSenal.RPM -> R.string.vehicle_can_test_rpm
            ClavesSenal.ACELERADOR -> R.string.vehicle_can_test_throttle
            ClavesSenal.VELOCIDAD, ClavesSenal.VELOCIDAD_RUEDA -> R.string.vehicle_can_test_speed
            ClavesSenal.TEMP_MOTOR, ClavesSenal.TEMP_AIRE -> R.string.vehicle_can_test_temperature
            else -> R.string.vehicle_can_test_signal
        },
    )

    fun detalle(senal: SenalCan, resolver: StringResolver): String {
        val byte = senal.bitInicio / 8
        val ultimo = byte + (senal.bitInicio % 8 + senal.largo - 1) / 8
        val campo = when {
            senal.largo < 8 -> resolver.get(R.string.vehicle_can_detail_byte_bit, byte, senal.bitInicio % 8)
            ultimo > byte -> resolver.get(R.string.vehicle_can_detail_bytes, byte, ultimo)
            else -> resolver.get(R.string.vehicle_can_detail_byte, byte)
        }
        val escala = if (senal.factor == 1.0 && senal.offset == 0.0) {
            resolver.get(R.string.vehicle_can_detail_unscaled)
        } else {
            val factor = LocaleNumbers.decimal(senal.factor, decimals(senal.factor), resolver.locale)
            val offset = LocaleNumbers.decimal(kotlin.math.abs(senal.offset), decimals(senal.offset), resolver.locale)
            val offsetText = if (senal.offset < 0) {
                resolver.get(R.string.vehicle_can_scale_negative, offset)
            } else {
                resolver.get(R.string.vehicle_can_scale_positive, offset)
            }
            resolver.get(R.string.vehicle_can_detail_scale, factor, offsetText)
        }
        return resolver.get(R.string.vehicle_can_detail, senal.id, campo, escala)
    }

    private fun decimals(value: Double): Int =
        value.toString().substringAfter('.', "").length.coerceAtMost(3)

    private val clavesPid = setOf(
        ClavesSenal.RPM,
        ClavesSenal.VELOCIDAD,
        ClavesSenal.TEMP_MOTOR,
        ClavesSenal.TEMP_AIRE,
        ClavesSenal.ACELERADOR,
    )
}
