package com.revscope.core.obd.diagnostics.uds

import com.revscope.core.obd.connection.Transport

/** Manda un comando ya validado: con header va dirigido (AT H1, la respuesta trae headers); sin header, al destino por defecto. */
object EnvioUds {
    const val TIMEOUT_MS = 3_000L

    suspend fun enviar(bt: Transport, header: String?, comando: String, timeoutMs: Long = TIMEOUT_MS): String =
        if (header == null) bt.exchange("$comando\r", timeoutMs) else bt.targetedExchange(header, comando, timeoutMs)

    fun interpretar(raw: String, comando: ComandoValidado, header: String?): List<RespuestaUds> =
        ParserUds.interpretar(raw, comando.bytes.firstOrNull() ?: 0, conHeaders = header != null)
}
