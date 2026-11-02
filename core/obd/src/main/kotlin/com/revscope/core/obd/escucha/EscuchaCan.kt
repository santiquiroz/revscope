package com.revscope.core.obd.escucha

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.protocol.ResponseParser
import javax.inject.Inject

// Defaults compartidos: dos OpcionesEscucha sin lambdas propias siguen siendo iguales.
private val NUNCA_DETENER: () -> Boolean = { false }
private val SIN_PROGRESO: (Int, Int) -> Unit = { _, _ -> }

data class OpcionesEscucha(
    val etiqueta: String,
    val duracionMs: Long,
    val filtro: String?,
    val detener: () -> Boolean = NUNCA_DETENER,
    val alProgreso: (tramas: Int, desbordes: Int) -> Unit = SIN_PROGRESO,
)

private class Acumulado(val inicio: Long, val limiteMs: Long) {
    val tramas = mutableListOf<TramaCan>()
    val huecos = mutableListOf<Hueco>()
    var desbordes = 0
    var errores = 0
    var huecoAbiertoDesde: Long? = null
}

class EscuchaCan internal constructor(private val ahora: () -> Long) {

    @Inject
    constructor() : this(System::currentTimeMillis)

    suspend fun capturar(bt: Transport, opciones: OpcionesEscucha): CapturaCan {
        val protocolo = leerProtocolo(bt)
        val bits29 = protocolo == "7" || protocolo == "9"
        val a = Acumulado(ahora(), opciones.duracionMs)
        var silencioso = true
        try {
            bt.exchange("AT H1\r")
            bt.exchange("AT S0\r")
            bt.exchange("AT CAF0\r")
            silencioso = configurarSilencioso(bt)
            if (opciones.filtro != null) bt.exchange("AT CRA ${opciones.filtro}\r")
            monitorear(bt, opciones, bits29, a)
        } finally {
            restaurar(bt)
        }
        a.huecoAbiertoDesde?.let { cerrarHueco(a, it) }
        return CapturaCan(
            id = "${a.inicio}-${normalizarEtiqueta(opciones.etiqueta)}",
            etiqueta = opciones.etiqueta,
            inicioEpochMs = a.inicio,
            duracionMs = if (opciones.detener()) tiempoRelativo(a) else opciones.duracionMs,
            protocolo = protocolo,
            bits29 = bits29,
            filtro = opciones.filtro,
            silencioso = silencioso,
            desbordes = a.desbordes,
            errores = a.errores,
            tramas = a.tramas.toList(),
            huecos = a.huecos.toList(),
        )
    }

    private suspend fun monitorear(bt: Transport, opciones: OpcionesEscucha, bits29: Boolean, a: Acumulado) {
        var relanzamientos = 0
        do {
            if (opciones.detener()) break
            val restante = (a.limiteMs - tiempoRelativo(a)).coerceAtLeast(0)
            if (restante == 0L) break
            val huboDesborde = monitorearUnaVuelta(bt, restante, opciones, bits29, a)
            if (!huboDesborde || tiempoRelativo(a) >= a.limiteMs || relanzamientos >= MAX_RELANZAMIENTOS) break
            relanzamientos++
        } while (true)
    }

    private suspend fun monitorearUnaVuelta(
        bt: Transport,
        restante: Long,
        opciones: OpcionesEscucha,
        bits29: Boolean,
        a: Acumulado,
    ): Boolean {
        var primeraLinea = true
        var huboDesborde = false
        bt.monitorear("AT MA", restante, opciones.detener) { linea ->
            if (primeraLinea) {
                a.huecoAbiertoDesde?.let { cerrarHueco(a, it) }
                primeraLinea = false
            }
            if (LineaMonitor.esDesborde(linea)) {
                a.desbordes++
                huboDesborde = true
                if (a.huecoAbiertoDesde == null) a.huecoAbiertoDesde = tiempoRelativo(a)
            } else {
                clasificar(linea, bits29, a)
            }
            opciones.alProgreso(a.tramas.size, a.desbordes)
        }
        return huboDesborde
    }

    private fun cerrarHueco(a: Acumulado, desde: Long) {
        a.huecos += Hueco(desde, maxOf(desde, tiempoRelativo(a)))
        a.huecoAbiertoDesde = null
    }

    private fun tiempoRelativo(a: Acumulado): Long =
        (ahora() - a.inicio).coerceIn(0L, a.limiteMs)

    private suspend fun leerProtocolo(bt: Transport): String {
        val numero = ResponseParser.cleanResponse(bt.exchange("AT DPN\r")).removePrefix("A")
        if (numero !in PROTOCOLOS_CAN) {
            throw IllegalStateException("el protocolo $numero no es CAN; el modo escucha solo funciona con CAN")
        }
        return numero
    }

    private suspend fun configurarSilencioso(bt: Transport): Boolean =
        !ResponseParser.cleanResponse(bt.exchange("AT CSM1\r")).contains("?")

    private fun clasificar(linea: String, bits29: Boolean, a: Acumulado) {
        if (LineaMonitor.esError(linea)) {
            a.errores++
            return
        }
        LineaMonitor.parsear(linea, ahora() - a.inicio, bits29)?.let { a.tramas.add(it) }
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
