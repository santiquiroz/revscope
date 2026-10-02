package com.revscope.core.obd.diagnostics.uds

data class TramaRespuesta(val header: String?, val payload: List<Int>)

sealed interface RespuestaUds {
    val header: String?

    data class Positiva(override val header: String?, val servicio: Int, val datos: List<Int>) : RespuestaUds
    data class Negativa(override val header: String?, val servicio: Int, val nrc: Int, val nombreNrc: String) : RespuestaUds
    data class SinRespuesta(override val header: String?, val motivo: String) : RespuestaUds
}

object ParserUds {
    private val whitespace = Regex("\\s+")
    private val bytePair = Regex("^[0-9A-F]{2}$")
    private val indexedLine = Regex("^(\\d+)\\s*:\\s*(.*)$")
    private val searchingPrefix = Regex("^SEARCHING\\.\\.\\.\\s*", RegexOption.IGNORE_CASE)

    fun tramas(raw: String, conHeaders: Boolean): List<TramaRespuesta> {
        val lineas = prepararLineas(raw)
        return if (conHeaders) tramasConHeaders(lineas) else tramasSinHeaders(lineas)
    }

    fun interpretar(raw: String, servicioPedido: Int, conHeaders: Boolean): List<RespuestaUds> {
        val motivoError = motivoError(raw)
        if (motivoError != null) return listOf(RespuestaUds.SinRespuesta(null, motivoError))

        val respuestas = tramas(raw, conHeaders)
        if (respuestas.isEmpty()) {
            return listOf(RespuestaUds.SinRespuesta(null, "respuesta no reconocida: ${prepararLineas(raw).joinToString(" ")}"))
        }
        return respuestas.mapIndexedNotNull { indice, trama ->
            val respuesta = interpretarTrama(trama, servicioPedido)
            if (respuesta is RespuestaUds.Negativa &&
                respuesta.nrc == 0x78 &&
                respuestas.drop(indice + 1).any { it.header == trama.header }
            ) {
                null
            } else {
                respuesta
            }
        }
    }

    fun nombreNrc(nrc: Int): String = when (nrc) {
        0x10 -> "rechazo general"
        0x11 -> "servicio no soportado"
        0x12 -> "subfunción no soportada"
        0x13 -> "longitud o formato incorrecto"
        0x14 -> "respuesta demasiado larga"
        0x21 -> "ocupado, repetir"
        0x22 -> "condiciones no correctas"
        0x24 -> "secuencia incorrecta"
        0x31 -> "fuera de rango (DID/rutina inexistente)"
        0x33 -> "acceso de seguridad denegado"
        0x35 -> "clave inválida"
        0x36 -> "demasiados intentos"
        0x37 -> "espera obligatoria antes de reintentar"
        0x70 -> "carga no aceptada"
        0x72 -> "fallo de programación"
        0x78 -> "respuesta pendiente"
        0x7E -> "subfunción no soportada en la sesión activa"
        0x7F -> "servicio no soportado en la sesión activa"
        0x81 -> "RPM demasiado altas"
        0x83 -> "motor en marcha"
        0x88 -> "velocidad demasiado alta"
        else -> "NRC 0x${nrc.toString(16).uppercase().padStart(2, '0')}"
    }

    fun hex(bytes: List<Int>): String =
        bytes.joinToString(" ") { it.toString(16).uppercase().padStart(2, '0') }

    private fun prepararLineas(raw: String): List<String> =
        raw.split(Regex("[\\r\\n]+"))
            .map(::limpiarLinea)
            .filter(String::isNotEmpty)

    private fun limpiarLinea(linea: String): String =
        searchingPrefix.replace(linea.trim().removeSuffix(">"), "").trim().uppercase()

    private fun motivoError(raw: String): String? {
        val lineas = prepararLineas(raw)
        if (lineas.isEmpty()) return "sin respuesta"

        val texto = lineas.joinToString(" ").replace(whitespace, " ")
        val compacto = texto.replace(whitespace, "")
        val esError = compacto in setOf(
            "NODATA",
            "CANERROR",
            "BUSERROR",
            "UNABLETOCONNECT",
            "STOPPED",
            "?",
        ) || (compacto.startsWith("BUSINIT") && compacto.endsWith("ERROR"))
        return texto.takeIf { esError }
    }

    private fun tramasConHeaders(lineas: List<String>): List<TramaRespuesta> {
        val completas = mutableListOf<TramaRespuesta>()
        val pendientes = mutableMapOf<String, MensajePendiente>()

        lineas.forEach { linea ->
            if (linea.length < 4) return@forEach

            val header = linea.take(3)
            if (!header.all { it.isDigit() || it in 'A'..'F' }) return@forEach
            val bytes = bytesHex(linea.drop(3)) ?: return@forEach
            val pci = bytes.getOrNull(0) ?: return@forEach
            val tipo = pci ushr 4

            when (tipo) {
                0x0 -> {
                    val longitud = pci and 0x0F
                    val payload = bytes.drop(1).take(longitud)
                    if (payload.size == longitud) completas += TramaRespuesta(header, payload)
                }
                0x1 -> {
                    val segundoByte = bytes.getOrNull(1) ?: return@forEach
                    val longitud = ((pci and 0x0F) shl 8) or segundoByte
                    val datos = bytes.drop(2).take(6).toMutableList()
                    if (longitud <= datos.size) {
                        completas += TramaRespuesta(header, datos.take(longitud))
                        pendientes.remove(header)
                    } else {
                        pendientes[header] = MensajePendiente(longitud, datos)
                    }
                }
                0x2 -> {
                    val mensaje = pendientes[header] ?: return@forEach
                    mensaje.datos.addAll(bytes.drop(1).take(7))
                    if (mensaje.datos.size >= mensaje.longitud) {
                        completas += TramaRespuesta(header, mensaje.datos.take(mensaje.longitud))
                        pendientes.remove(header)
                    }
                }
            }
        }
        return completas
    }

    private fun tramasSinHeaders(lineas: List<String>): List<TramaRespuesta> {
        val tramas = mutableListOf<TramaRespuesta>()
        var indice = 0

        while (indice < lineas.size) {
            val longitud = lineas[indice].toIntOrNull(16)
                ?.takeIf { lineas[indice].length == 3 }
            if (longitud != null && longitud >= 0 && indice + 1 < lineas.size &&
                indexedLine.matches(lineas[indice + 1])
            ) {
                val bloque = mutableListOf<Pair<Int, List<Int>>>()
                var cursor = indice + 1
                while (cursor < lineas.size) {
                    val coincidencia = indexedLine.matchEntire(lineas[cursor]) ?: break
                    val numero = coincidencia.groupValues[1].toIntOrNull() ?: break
                    val contenido = coincidencia.groupValues[2].replace(whitespace, "").uppercase()
                    val bytes = bytesHex(contenido) ?: break
                    bloque += numero to bytes
                    cursor++
                }
                val payload = bloque.sortedBy { it.first }.flatMap { it.second }.take(longitud)
                if (payload.size == longitud) tramas += TramaRespuesta(null, payload)
                indice = cursor
            } else {
                val contenido = lineas[indice].replace(whitespace, "").uppercase()
                val bytes = bytesHex(contenido)
                if (bytes != null) tramas += TramaRespuesta(null, bytes)
                indice++
            }
        }
        return tramas
    }

    private fun interpretarTrama(trama: TramaRespuesta, servicioPedido: Int): RespuestaUds {
        val servicio = trama.payload.firstOrNull()
            ?: return RespuestaUds.SinRespuesta(trama.header, "respuesta inesperada: ")
        return when {
            servicio == servicioPedido + 0x40 ->
                RespuestaUds.Positiva(trama.header, servicioPedido, trama.payload.drop(1))
            servicio == 0x7F && trama.payload.size >= 3 ->
                RespuestaUds.Negativa(
                    trama.header,
                    trama.payload[1],
                    trama.payload[2],
                    nombreNrc(trama.payload[2]),
                )
            else ->
                RespuestaUds.SinRespuesta(trama.header, "respuesta inesperada: ${hex(trama.payload)}")
        }
    }

    private fun bytesHex(texto: String): List<Int>? {
        val compacto = texto.replace(whitespace, "").uppercase()
        if (compacto.isEmpty() || compacto.length % 2 != 0) return null
        val pares = compacto.chunked(2)
        if (pares.any { !bytePair.matches(it) }) return null
        return pares.map { it.toInt(16) }
    }

    private data class MensajePendiente(val longitud: Int, val datos: MutableList<Int>)
}
