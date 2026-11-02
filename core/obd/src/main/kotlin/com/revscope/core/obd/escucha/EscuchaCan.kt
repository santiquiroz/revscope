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
        val huecos = mutableListOf<Hueco>()
        var desbordes = 0
        var errores = 0
        var silencioso = true
        var huecoAbiertoDesde: Long? = null
        try {
            bt.exchange("AT H1\r")
            bt.exchange("AT S0\r")
            bt.exchange("AT CAF0\r")
            silencioso = configurarSilencioso(bt)
            if (opciones.filtro != null) bt.exchange("AT CRA ${opciones.filtro}\r")
            monitorear(
                bt, opciones.duracionMs, bits29, inicio, tramas, huecos,
                { desbordes++ }, { errores++ },
                { huecoAbiertoDesde }, { huecoAbiertoDesde = it },
            )
        } finally {
            restaurar(bt)
        }
        huecoAbiertoDesde?.let { cerrarHueco(huecos, it, tiempoRelativo(inicio, opciones.duracionMs)) }
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
            huecos = huecos.toList(),
        )
    }

    private suspend fun monitorear(
        bt: Transport,
        duracionMs: Long,
        bits29: Boolean,
        inicio: Long,
        tramas: MutableList<TramaCan>,
        huecos: MutableList<Hueco>,
        contarDesborde: () -> Unit,
        contarError: () -> Unit,
        huecoAbierto: () -> Long?,
        actualizarHueco: (Long?) -> Unit,
    ) {
        var relanzamientos = 0
        do {
            val restante = (duracionMs - tiempoRelativo(inicio, duracionMs)).coerceAtLeast(0)
            if (restante == 0L) break
            val huboDesborde = monitorearUnaVuelta(
                bt, restante, duracionMs, bits29, inicio, tramas, huecos,
                contarDesborde, contarError, huecoAbierto, actualizarHueco,
            )
            if (!huboDesborde || tiempoRelativo(inicio, duracionMs) >= duracionMs ||
                relanzamientos >= MAX_RELANZAMIENTOS
            ) break
            relanzamientos++
        } while (true)
    }

    private suspend fun monitorearUnaVuelta(
        bt: Transport,
        restante: Long,
        duracionMs: Long,
        bits29: Boolean,
        inicio: Long,
        tramas: MutableList<TramaCan>,
        huecos: MutableList<Hueco>,
        contarDesborde: () -> Unit,
        contarError: () -> Unit,
        huecoAbierto: () -> Long?,
        actualizarHueco: (Long?) -> Unit,
    ): Boolean {
        var primeraLinea = true
        var huboDesborde = false
        bt.monitorear("AT MA", restante) { linea ->
            if (primeraLinea) {
                huecoAbierto()?.let { desde ->
                    cerrarHueco(huecos, desde, tiempoRelativo(inicio, duracionMs))
                    actualizarHueco(null)
                }
                primeraLinea = false
            }
            if (LineaMonitor.esDesborde(linea)) {
                contarDesborde()
                huboDesborde = true
                if (huecoAbierto() == null) actualizarHueco(tiempoRelativo(inicio, duracionMs))
            } else {
                clasificar(linea, bits29, inicio, tramas, {}, contarError)
            }
        }
        return huboDesborde
    }

    private fun cerrarHueco(huecos: MutableList<Hueco>, desde: Long, hasta: Long) {
        huecos += Hueco(desde, maxOf(desde, hasta))
    }

    private fun tiempoRelativo(inicio: Long, limiteMs: Long): Long =
        (ahora() - inicio).coerceIn(0L, limiteMs)

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
        private const val MAX_RELANZAMIENTOS = 50
        private val PROTOCOLOS_CAN = setOf("6", "7", "8", "9")
        private val RESTAURACION = listOf("AT CAF1\r", "AT AR\r", "AT S0\r", "AT H0\r")

        fun normalizarEtiqueta(etiqueta: String): String =
            etiqueta.lowercase().map { if (it in 'a'..'z' || it in '0'..'9' || it == '_') it else '_' }
                .joinToString("").take(32)
    }
}
