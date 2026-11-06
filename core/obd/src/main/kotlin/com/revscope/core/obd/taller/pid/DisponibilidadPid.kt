package com.revscope.core.obd.taller.pid

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.pid.PidRegistry

data class DisponibilidadPid(
    val pid: String,
    val estado: EstadoSoporte,
    val motivo: String?,
) {
    val puedeConsultarse: Boolean get() = estado != EstadoSoporte.NoSoportado

    companion object {
        fun resolver(pid: String, nombre: String, registry: PidRegistry, stringResolver: StringResolver): DisponibilidadPid =
            resolver(pid, nombre, registry.estadoSoporte(pid), stringResolver)

        fun resolver(pid: String, nombre: String, estado: EstadoSoporte, stringResolver: StringResolver): DisponibilidadPid {
            val pidNormalizado = pid.trim().uppercase().padStart(2, '0')
            return DisponibilidadPid(
                pid = pidNormalizado,
                estado = estado,
                motivo = motivo(pidNormalizado, nombre.trim(), estado, stringResolver),
            )
        }

        private fun motivo(pid: String, nombre: String, estado: EstadoSoporte, resolver: StringResolver): String? = when (estado) {
            EstadoSoporte.Soportado -> null
            EstadoSoporte.NoSoportado -> resolver.get(R.string.obd_pid_unavailable, nombre, pid)
            EstadoSoporte.Desconocido -> resolver.get(R.string.obd_pid_unannounced)
        }
    }
}
