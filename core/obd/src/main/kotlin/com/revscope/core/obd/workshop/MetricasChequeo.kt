package com.revscope.core.obd.workshop

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.protocol.ReadinessParser
import com.revscope.core.obd.session.ObdSessionManager

data class MetricasChequeo(
    val stft: Double? = null,
    val ltft: Double? = null,
    val voltaje: Double? = null,
    val motorEncendido: Boolean? = null,
    val ect: Double? = null,
    val monitoresCompletos: Int? = null,
    val monitoresTotales: Int? = null,
    val dtcs: List<String> = emptyList(),
    val dtcsLeidos: Boolean = true,
) {
    val correccionTotal: Double?
        get() = if (stft != null && ltft != null) stft + ltft else null

    companion object {
        private const val PID_STFT = "06"
        private const val PID_LTFT = "07"
        private const val PID_RPM = "0C"
        private const val PID_ECT = "05"
        private const val RPM_MOTOR_ENCENDIDO = 400.0

        fun desde(
            lecturas: Map<String, ObdReading>,
            readiness: ReadinessParser.ReadinessStatus?,
            dtcs: List<String>?,
        ): MetricasChequeo {
            val soportados = readiness?.monitors?.filter { it.soportado }
            return MetricasChequeo(
                stft = lecturas[PID_STFT]?.value,
                ltft = lecturas[PID_LTFT]?.value,
                voltaje = lecturas[ObdSessionManager.VBAT_PID]?.value,
                motorEncendido = lecturas[PID_RPM]?.let { it.value > RPM_MOTOR_ENCENDIDO },
                ect = lecturas[PID_ECT]?.value,
                monitoresCompletos = soportados?.count { it.completo },
                monitoresTotales = soportados?.size,
                dtcs = dtcs.orEmpty().distinct(),
                dtcsLeidos = dtcs != null,
            )
        }
    }
}
