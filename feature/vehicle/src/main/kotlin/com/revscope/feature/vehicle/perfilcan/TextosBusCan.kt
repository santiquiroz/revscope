package com.revscope.feature.vehicle.perfilcan

import com.revscope.core.obd.perfilcan.ClavesSenal
import com.revscope.core.obd.perfilcan.FuenteSenal
import com.revscope.core.obd.perfilcan.ModoFuente
import com.revscope.core.obd.perfilcan.SenalCan

internal object TextosBusCan {

    const val SIN_CONEXION = "Conecta el adaptador con la llave en ON para probar en vivo."
    const val SIN_CAN = "Tu vehículo no usa CAN: no hay bus que escuchar."
    const val PREGUNTA = "¿El valor de arriba coincide con lo que hace tu vehículo?"
    const val ESPERANDO = "Esperando datos…"

    private val clavesPid = setOf(
        ClavesSenal.RPM, ClavesSenal.VELOCIDAD, ClavesSenal.TEMP_MOTOR, ClavesSenal.TEMP_AIRE, ClavesSenal.ACELERADOR,
    )

    fun modo(modo: ModoFuente): String = when (modo) {
        ModoFuente.ESTANDAR -> "Estándar"
        ModoFuente.COMBINADO -> "Combinado"
        ModoFuente.SOLO_BUS -> "Solo bus"
    }

    fun descripcionModo(modo: ModoFuente): String = when (modo) {
        ModoFuente.ESTANDAR -> "Como hoy, sin cambios: solo el OBD estándar."
        ModoFuente.COMBINADO -> "El OBD estándar más escuchas cortas del bus tras cada ronda. Recomendado."
        ModoFuente.SOLO_BUS -> "Escucha el bus todo el tiempo; los datos que no estén en el perfil dejan de leerse."
    }

    fun fuente(fuente: FuenteSenal): String = when (fuente) {
        FuenteSenal.AUTO -> "Automática"
        FuenteSenal.PID -> "OBD"
        FuenteSenal.BUS -> "Bus"
        FuenteSenal.GPS -> "GPS"
        FuenteSenal.CALCULADA -> "Calculada"
    }

    fun opciones(clave: String): List<FuenteSenal> = buildList {
        add(FuenteSenal.AUTO)
        if (clave in clavesPid) add(FuenteSenal.PID)
        add(FuenteSenal.BUS)
        if (clave == ClavesSenal.VELOCIDAD) add(FuenteSenal.GPS)
        if (clave == ClavesSenal.GEAR) add(FuenteSenal.CALCULADA)
    }

    fun instruccion(clave: String): String = when (clave) {
        ClavesSenal.GEAR -> "Cambia de marcha y mira si coincide"
        ClavesSenal.PATA_LATERAL -> "Mueve la pata lateral y mira si cambia"
        ClavesSenal.EMBRAGUE -> "Aprieta y suelta el embrague y mira si cambia"
        ClavesSenal.KILL_SWITCH -> "Pasa el kill switch a OFF y de vuelta a RUN, y mira si cambia"
        ClavesSenal.MOTOR_EN_MARCHA -> "Enciende y apaga el motor y mira si cambia"
        ClavesSenal.RPM -> "Acelera un poco y compara con el tacómetro"
        ClavesSenal.ACELERADOR -> "Gira el acelerador y mira si sube y baja"
        ClavesSenal.VELOCIDAD, ClavesSenal.VELOCIDAD_RUEDA -> "Empuja la moto caminando y compara con el velocímetro"
        ClavesSenal.TEMP_MOTOR, ClavesSenal.TEMP_AIRE -> "Compara con la temperatura que muestra tu tablero"
        else -> "Haz que la señal cambie y mira si el valor la sigue"
    }

    fun detalle(senal: SenalCan): String {
        val byte = senal.bitInicio / 8
        val ultimo = byte + (senal.bitInicio % 8 + senal.largo - 1) / 8
        val campo = when {
            senal.largo < 8 -> "byte $byte · bit ${senal.bitInicio % 8}"
            ultimo > byte -> "bytes $byte-$ultimo"
            else -> "byte $byte"
        }
        val escala = if (senal.factor == 1.0 && senal.offset == 0.0) "sin escala" else "×${senal.factor} ${conSigno(senal.offset)}"
        return "ID ${senal.id} · $campo · $escala"
    }

    private fun conSigno(offset: Double): String = if (offset < 0) "− ${-offset}" else "+ $offset"
}
