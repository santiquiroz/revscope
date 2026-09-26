package com.revscope.core.obd.protocol

/** Servicio OBD que pide la lista de DTC y el byte con que empieza su respuesta positiva. */
enum class DtcServicio(val comando: String, val respuesta: String) {
    ACTIVOS("03", "43"),
    PENDIENTES("07", "47"),
    PERMANENTES("0A", "4A"),
}

/**
 * Decodifica las respuestas de los servicios 03/07/0A del ELM327 (headers apagados).
 *
 * - CAN (ISO 15765-4): tras el byte de servicio viene el CONTEO de DTC y luego 2 bytes por código.
 * - K-line / J1850: tramas fijas de 3 pares por línea, rellenas con 0000, sin conteo.
 * - Cada línea puede venir de otra ECU; los mensajes ISO-TP ("00A", "0:…", "1:…") se aplanan.
 *
 * [esCan] null = protocolo desconocido: se infiere por paridad (CAN deja un número impar de
 * bytes tras el servicio: 1 de conteo + 2N; K-line siempre deja un número par).
 */
object DtcResponseParser {

    private val LINE_BREAKS = Regex("[\\r\\n]+")
    private val ISO_TP_LENGTH = Regex("^[0-9A-F]{3}$")
    private val ISO_TP_SEGMENT = Regex("^[0-9A-F]:(.*)$")
    private val PREFIJOS = charArrayOf('P', 'C', 'B', 'U')

    fun parse(raw: String, servicio: DtcServicio, esCan: Boolean?): List<String> =
        mensajes(raw)
            .flatMap { decodificarMensaje(it, servicio, esCan) }
            .distinct()

    fun decodificarPar(high: Int, low: Int): String {
        val prefijo = PREFIJOS[(high shr 6) and 0x03]
        val d1 = (high shr 4) and 0x03
        return prefijo + "$d1" + hexDigit(high and 0x0F) + hexDigit((low shr 4) and 0x0F) + hexDigit(low and 0x0F)
    }

    private fun hexDigit(value: Int): Char = value.toString(16).uppercase().single()

    private class Mensaje(val hex: String, val largoBytes: Int?)

    private fun mensajes(raw: String): List<Mensaje> {
        val result = mutableListOf<Mensaje>()
        var multiFrame: StringBuilder? = null
        var largo: Int? = null
        for (linea in lineasLimpias(raw)) {
            val segmento = ISO_TP_SEGMENT.find(linea)
            when {
                ISO_TP_LENGTH.matches(linea) -> {
                    multiFrame?.let { result += Mensaje(it.toString(), largo) }
                    multiFrame = StringBuilder()
                    largo = linea.toInt(16)
                }
                segmento != null && multiFrame != null -> multiFrame.append(segmento.groupValues[1])
                else -> result += Mensaje(linea, null)
            }
        }
        multiFrame?.let { result += Mensaje(it.toString(), largo) }
        return result
    }

    private fun lineasLimpias(raw: String): List<String> =
        raw.split(LINE_BREAKS)
            .map { ResponseParser.stripTransientPrefixes(ResponseParser.cleanResponse(it)) }
            .filter { it.isNotEmpty() }

    private fun decodificarMensaje(mensaje: Mensaje, servicio: DtcServicio, esCan: Boolean?): List<String> {
        if (!mensaje.hex.startsWith(servicio.respuesta)) return emptyList()
        val bytes = bytesDe(mensaje) ?: return emptyList()
        val datos = bytes.drop(1)
        val can = esCan ?: (datos.size % 2 == 1)
        return if (can) codigosCan(datos) else codigosSinConteo(datos)
    }

    private fun bytesDe(mensaje: Mensaje): List<Int>? {
        val hex = mensaje.hex.let { if (it.length % 2 == 0) it else it.dropLast(1) }
        val bytes = ResponseParser.hexToBytes(hex)?.map { it.toInt() and 0xFF } ?: return null
        return mensaje.largoBytes?.let { bytes.take(it) } ?: bytes
    }

    private fun codigosCan(datos: List<Int>): List<String> {
        val conteo = datos.firstOrNull() ?: return emptyList()
        val pares = datos.drop(1).chunked(2).filter { it.size == 2 }.take(conteo)
        return pares.filterNot(::esRelleno).map { decodificarPar(it[0], it[1]) }
    }

    private fun codigosSinConteo(datos: List<Int>): List<String> =
        datos.chunked(2)
            .filter { it.size == 2 }
            .filterNot(::esRelleno)
            .map { decodificarPar(it[0], it[1]) }

    private fun esRelleno(par: List<Int>): Boolean = par[0] == 0 && par[1] == 0
}
