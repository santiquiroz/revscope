package com.revscope.core.obd.escucha

enum class ClaseByte { CONSTANTE, CONTADOR, CHECKSUM, SENAL }

data class AnalisisByte(
    val id: String,
    val byte: Int,
    val clase: ClaseByte,
    val distintos: Int,
    val minimo: Int,
    val maximo: Int,
    val bitsQueCambian: List<Int>,
    val detalle: String?,
)

object AnalisisBitsCan {

    fun analizar(tramas: List<TramaCan>): List<AnalisisByte> =
        tramas.groupBy { IdCan.texto(it.id, it.extendido) }
            .toSortedMap()
            .flatMap { (id, grupo) -> analizarId(id, grupo) }
            .sortedWith(compareBy<AnalisisByte>({ it.id }, { it.byte }))

    private fun analizarId(id: String, tramas: List<TramaCan>): List<AnalisisByte> {
        val cantidadBytes = tramas.maxOfOrNull { it.datos.size } ?: return emptyList()
        return (0 until cantidadBytes).mapNotNull { indice ->
            val valores = tramas.mapNotNull { it.datos.getOrNull(indice) }
            if (valores.isEmpty()) null else analizarByte(id, indice, valores, tramas)
        }
    }

    private fun analizarByte(
        id: String,
        indice: Int,
        valores: List<Int>,
        tramas: List<TramaCan>,
    ): AnalisisByte {
        val distintos = valores.toSet().size
        val contador = detectarContador(valores, tramas)
        val checksum = if (contador == null) detectarChecksum(tramas, indice) else null
        val clase = when {
            distintos == 1 -> ClaseByte.CONSTANTE
            contador != null -> ClaseByte.CONTADOR
            checksum != null -> ClaseByte.CHECKSUM
            else -> ClaseByte.SENAL
        }
        return AnalisisByte(
            id = id,
            byte = indice,
            clase = clase,
            distintos = distintos,
            minimo = valores.min(),
            maximo = valores.max(),
            bitsQueCambian = (0..7).filter { bit -> valores.map { (it shr bit) and 1 }.toSet().size > 1 },
            detalle = when (clase) {
                ClaseByte.CONTADOR -> contador
                ClaseByte.CHECKSUM -> checksum
                else -> null
            },
        )
    }

    private fun detectarContador(valores: List<Int>, tramas: List<TramaCan>): String? {
        if (tramas.size < MIN_TRAMAS || valores.size < MIN_TRAMAS) return null
        return detectarContadorBits(valores, 4)?.let { "contador de 4 bits, paso $it" } // i18n-ignore
            ?: detectarContadorBits(valores, 8)?.let { "contador de 8 bits, paso $it" } // i18n-ignore
    }

    private fun detectarContadorBits(valores: List<Int>, bits: Int): Int? {
        if (bits == 4 && valores.map { it and 0xF0 }.toSet().size != 1) return null
        val mascara = (1 shl bits) - 1
        return (1..2).firstOrNull { paso ->
            val validos = valores.zipWithNext().count { (anterior, actual) ->
                ((actual - anterior) and mascara) == paso
            }
            validos >= (valores.size - 1) * UMBRAL_PASOS
        }
    }

    private fun detectarChecksum(tramas: List<TramaCan>, indice: Int): String? {
        if (tramas.size < MIN_TRAMAS || tramas.any { indice >= it.datos.size }) return null
        return algoritmosChecksum().firstOrNull { (nombre, calcular) ->
            tramas.all { trama -> trama.datos[indice] == calcular(trama.datos, indice) }
        }?.first
    }

    private fun algoritmosChecksum(): List<Pair<String, (List<Int>, Int) -> Int>> =
        buildList<Pair<String, (List<Int>, Int) -> Int>> {
            add("xor" to { bytes: List<Int>, indice: Int ->
                bytes.filterIndexed { i, _ -> i != indice }.reduceOrNull { actual, valor -> actual xor valor } ?: 0
            })
            add("suma" to { bytes: List<Int>, indice: Int ->
                bytes.filterIndexed { i, _ -> i != indice }.sum() and 0xFF
            })
            add("~suma" to { bytes: List<Int>, indice: Int ->
                (bytes.filterIndexed { i, _ -> i != indice }.sum().inv()) and 0xFF
            })
            listOf(0x1D, 0x07).forEach { polinomio ->
                listOf(0x00, 0xFF).forEach { init ->
                    listOf(0x00, 0xFF).forEach { xorout ->
                        add(
                            "crc8 0x%02X init %02X xorout %02X".format(polinomio, init, xorout) to // i18n-ignore
                                { bytes: List<Int>, indice: Int ->
                                    crc8(bytes.filterIndexed { i, _ -> i != indice }, polinomio, init, xorout)
                                },
                        )
                    }
                }
            }
        }

    private fun crc8(bytes: List<Int>, polinomio: Int, init: Int, xorout: Int): Int {
        var crc = init
        bytes.forEach { byte ->
            crc = crc xor byte
            repeat(8) {
                crc = if (crc and 0x80 != 0) ((crc shl 1) xor polinomio) and 0xFF else (crc shl 1) and 0xFF
            }
        }
        return crc xor xorout
    }

    private const val MIN_TRAMAS = 8
    private const val UMBRAL_PASOS = 0.9
}
