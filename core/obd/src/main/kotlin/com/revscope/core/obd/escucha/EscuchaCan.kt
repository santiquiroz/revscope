package com.revscope.core.obd.escucha

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.protocol.ResponseParser
import javax.inject.Inject

data class OpcionesEscucha(
    val etiqueta: String,
    val duracionMs: Long,
    val filtro: String?,
)

class EscuchaCan internal constructor(private val ahora: () -> Long) {

    @Inject
    constructor() : this(System::currentTimeMillis)

    suspend fun capturar(bt: Transport, opciones: OpcionesEscucha): CapturaCan {
        val protocolo = leerProtocolo(bt)
        val bits29 = protocolo == "7" || protocolo == "9"
        val inicio = ahora()
        val tramas = mutableListOf<TramaCan>()
        var desbordes = 0
        var errores = 0
        var silencioso = true
        try {
            bt.exchange("AT H1\r")
            bt.exchange("AT S1\r")
            bt.exchange("AT CAF0\r")
            silencioso = configurarSilencioso(bt)
            if (opciones.filtro != null) bt.exchange("AT CRA ${opciones.filtro}\r")
            bt.monitorear("AT MA", opciones.duracionMs) { linea ->
                clasificar(linea, bits29, inicio, tramas, { desbordes++ }, { errores++ })
            }
        } finally {
            restaurar(bt)
        }
        return CapturaCan(
            id = "$inicio-${normalizarEtiqueta(opciones.etiqueta)}",
            etiqueta = opciones.etiqueta,
            inicioEpochMs = inicio,
            duracionMs = opciones.duracionMs,
            protocolo = protocolo,
            bits29 = bits29,
            filtro = opciones.filtro,
            silencioso = silencioso,
            desbordes = desbordes,
            errores = errores,
            tramas = tramas.toList(),
        )
    }

    private suspend fun leerProtocolo(bt: Transport): String {
        val numero = ResponseParser.cleanResponse(bt.exchange("AT DPN\r")).removePrefix("A")
        if (numero !in PROTOCOLOS_CAN) {
            throw IllegalStateException("el protocolo $numero no es CAN; el modo escucha solo funciona con CAN")
        }
        return numero
    }

    private suspend fun configurarSilencioso(bt: Transport): Boolean =
        !ResponseParser.cleanResponse(bt.exchange("AT CSM1\r")).contains("?")

    private fun clasificar(
        linea: String,
        bits29: Boolean,
        inicio: Long,
        tramas: MutableList<TramaCan>,
        contarDesborde: () -> Unit,
        contarError: () -> Unit,
    ) {
        when {
            LineaMonitor.esDesborde(linea) -> contarDesborde()
            LineaMonitor.esError(linea) -> contarError()
            else -> LineaMonitor.parsear(linea, ahora() - inicio, bits29)?.let { tramas.add(it) }
        }
    }

    private suspend fun restaurar(bt: Transport) {
        for (comando in RESTAURACION) runCatching { bt.exchange(comando) }
    }

    companion object {
        private val PROTOCOLOS_CAN = setOf("6", "7", "8", "9")
        private val RESTAURACION = listOf("AT CAF1\r", "AT AR\r", "AT S0\r", "AT H0\r")

        fun normalizarEtiqueta(etiqueta: String): String =
            etiqueta.lowercase().map { if (it in 'a'..'z' || it in '0'..'9' || it == '_') it else '_' }
                .joinToString("").take(32)
    }
}
