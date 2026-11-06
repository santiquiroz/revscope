package com.revscope.core.obd.taller.pid

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.protocol.ResponseParser

data class CapacidadesEcu(
    val codigoProtocolo: String?,
    val protocolo: String,
    val seleccionAutomatica: Boolean,
    val esKLine: Boolean,
    val pidsAnunciados: List<String>,
    val pidsPorRango: Map<String, List<String>>,
    val tasaEsperada: String,
    val tasaMedidaHz: Double?,
    val explicacion: String,
) {
    val cantidadPids: Int get() = pidsAnunciados.size

    companion object {
        val SIN_DATOS = CapacidadesEcu(null, "", false, false, emptyList(), emptyMap(), "", null, "")

        private val CODIGOS = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C')
        private val CODIGOS_K_LINE = setOf('3', '4', '5')
        private val CODIGOS_CAN = setOf('6', '7', '8', '9', 'A', 'B', 'C')
        private val RANGOS = linkedMapOf(
            "01 00" to 0x01..0x20,
            "01 20" to 0x21..0x40,
            "01 40" to 0x41..0x60,
            "01 60" to 0x61..0x80,
        )
        private val PID_HEX = Regex("^[0-9A-F]{1,2}$")

        fun desde(
            atDpn: String?,
            pidsAnunciados: Collection<String>,
            resolver: StringResolver,
            tasaMedidaHz: Double? = null,
        ): CapacidadesEcu {
            val protocolo = interpretarProtocolo(atDpn)
            val pids = normalizarPids(pidsAnunciados)
            return CapacidadesEcu(
                codigoProtocolo = protocolo.codigo?.toString(),
                protocolo = resolver.get(nombreProtocolo(protocolo.codigo)),
                seleccionAutomatica = protocolo.automatico,
                esKLine = protocolo.codigo in CODIGOS_K_LINE,
                pidsAnunciados = pids,
                pidsPorRango = agruparPorRango(pids),
                tasaEsperada = resolver.get(tasaEsperada(protocolo.codigo)),
                tasaMedidaHz = tasaMedidaHz?.takeIf { it.isFinite() && it > 0.0 },
                explicacion = resolver.get(explicacion(protocolo.codigo)),
            )
        }

        private fun interpretarProtocolo(atDpn: String?): ProtocoloDpn {
            val limpio = atDpn?.let(ResponseParser::cleanResponse).orEmpty()
            val automatico = limpio.length == 2 && limpio.startsWith('A')
            val codigo = if (automatico) limpio.last() else limpio.singleOrNull()
            return ProtocoloDpn(codigo?.takeIf { it in CODIGOS }, automatico)
        }

        private fun nombreProtocolo(codigo: Char?): Int = when (codigo) {
            '0' -> R.string.obd_pid_protocol_automatic
            '1' -> R.string.obd_pid_protocol_1
            '2' -> R.string.obd_pid_protocol_2
            '3' -> R.string.obd_pid_protocol_3
            '4' -> R.string.obd_pid_protocol_4
            '5' -> R.string.obd_pid_protocol_5
            '6' -> R.string.obd_pid_protocol_6
            '7' -> R.string.obd_pid_protocol_7
            '8' -> R.string.obd_pid_protocol_8
            '9' -> R.string.obd_pid_protocol_9
            'A' -> R.string.obd_pid_protocol_a
            'B' -> R.string.obd_pid_protocol_b
            'C' -> R.string.obd_pid_protocol_c
            else -> R.string.obd_pid_protocol_unknown
        }

        private fun normalizarPids(pids: Collection<String>): List<String> = pids
            .map(String::trim)
            .map(String::uppercase)
            .filter(PID_HEX::matches)
            .map { it.padStart(2, '0') }
            .filter { it.toInt(16) in 0x01..0x80 }
            .distinct()
            .sortedBy { it.toInt(16) }

        private fun agruparPorRango(pids: List<String>): Map<String, List<String>> = RANGOS.mapValues { (_, rango) ->
            pids.filter { it.toInt(16) in rango }
        }

        private fun tasaEsperada(codigo: Char?): Int = when (codigo) {
            in CODIGOS_K_LINE -> R.string.obd_pid_rate_low
            in CODIGOS_CAN -> R.string.obd_pid_rate_high
            '1', '2' -> R.string.obd_pid_rate_moderate
            else -> R.string.obd_pid_rate_unknown
        }

        private fun explicacion(codigo: Char?): Int =
            if (codigo in CODIGOS_K_LINE) R.string.obd_pid_kline_explanation else R.string.obd_pid_general_explanation
    }
}

private data class ProtocoloDpn(val codigo: Char?, val automatico: Boolean)
