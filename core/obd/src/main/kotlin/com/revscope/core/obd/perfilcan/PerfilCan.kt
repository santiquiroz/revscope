package com.revscope.core.obd.perfilcan

enum class TipoSenal { NUMERO, ENUM, BANDERA }

enum class OrdenBytes { BIG, LITTLE }

enum class EstadoSenal { VERIFICADA, SIN_VERIFICAR, APRENDIDA }

data class SenalCan(
    val clave: String,
    val nombre: String,
    val id: String,
    val bitInicio: Int,
    val largo: Int,
    val orden: OrdenBytes,
    val firmado: Boolean,
    val factor: Double,
    val offset: Double,
    val unidad: String,
    val tipo: TipoSenal,
    val valores: Map<Long, String>,
    val invalidos: Set<Long>,
    val periodoMs: Long,
    val estado: EstadoSenal,
    val origen: String,
)

data class VehiculoPerfil(
    val marca: String,
    val modelo: String,
    val anios: String,
    val tipo: String,
)

data class PerfilCan(
    val version: Int,
    val id: String,
    val vehiculo: VehiculoPerfil,
    val calibracionesEcu: List<String>,
    val protocolo: String,
    val licencia: String,
    val autor: String,
    val atribucion: String,
    val senales: List<SenalCan>,
)

object ClavesSenal {
    const val GEAR = "GEAR"
    const val RPM = "0C"
    const val VELOCIDAD = "0D"
    const val TEMP_MOTOR = "05"
    const val TEMP_AIRE = "0F"
    const val ACELERADOR = "11"
    const val PATA_LATERAL = "PATA_LATERAL"
    const val EMBRAGUE = "EMBRAGUE"
    const val KILL_SWITCH = "KILL_SWITCH"
    const val MOTOR_EN_MARCHA = "MOTOR_EN_MARCHA"
    const val VELOCIDAD_RUEDA = "VELOCIDAD_RUEDA"

    val todas: Set<String> = setOf(
        GEAR, RPM, VELOCIDAD, TEMP_MOTOR, TEMP_AIRE, ACELERADOR,
        PATA_LATERAL, EMBRAGUE, KILL_SWITCH, MOTOR_EN_MARCHA, VELOCIDAD_RUEDA,
    )
}
