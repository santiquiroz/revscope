package com.revscope.core.obd.taller.pid

import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.pid.PidRegistry

data class DisponibilidadPid(
    val pid: String,
    val estado: EstadoSoporte,
    val motivo: String?,
) {
    val puedeConsultarse: Boolean get() = estado != EstadoSoporte.NoSoportado

    companion object {
        fun resolver(pid: String, nombre: String, registry: PidRegistry): DisponibilidadPid =
            resolver(pid, nombre, registry.estadoSoporte(pid))

        fun resolver(pid: String, nombre: String, estado: EstadoSoporte): DisponibilidadPid {
            val pidNormalizado = pid.trim().uppercase().padStart(2, '0')
            return DisponibilidadPid(
                pid = pidNormalizado,
                estado = estado,
                motivo = motivo(pidNormalizado, nombre.trim(), estado),
            )
        }

        private fun motivo(pid: String, nombre: String, estado: EstadoSoporte): String? = when (estado) {
            EstadoSoporte.Soportado -> null
            EstadoSoporte.NoSoportado -> "Esta ECU no reporta $nombre (PID $pid)."
            EstadoSoporte.Desconocido -> "Aún no se conocen los PIDs que anuncia la ECU."
        }
    }
}
