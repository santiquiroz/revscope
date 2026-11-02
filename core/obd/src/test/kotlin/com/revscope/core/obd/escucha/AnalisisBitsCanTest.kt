package com.revscope.core.obd.escucha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalisisBitsCanTest {

    @Test
    fun `detecta byte constante`() {
        val resultado = AnalisisBitsCan.analizar(tramas(List(8) { listOf(0x5A) })).single()

        assertEquals(ClaseByte.CONSTANTE, resultado.clase)
        assertEquals(1, resultado.distintos)
        assertTrue(resultado.bitsQueCambian.isEmpty())
    }

    @Test
    fun `detecta contador de ocho bits con vuelta`() {
        val valores = listOf(0xFC, 0xFD, 0xFE, 0xFF, 0x00, 0x01, 0x02, 0x03)

        val resultado = AnalisisBitsCan.analizar(tramas(valores.map { listOf(it) })).single()

        assertEquals(ClaseByte.CONTADOR, resultado.clase)
        assertEquals("contador de 8 bits, paso 1", resultado.detalle)
    }

    @Test
    fun `detecta contador de nibble bajo con alto constante`() {
        val resultado = AnalisisBitsCan.analizar(tramas((0..7).map { listOf(0xA0 + it) })).single()

        assertEquals(ClaseByte.CONTADOR, resultado.clase)
        assertEquals("contador de 4 bits, paso 1", resultado.detalle)
    }

    @Test
    fun `detecta checksum xor`() {
        val resultado = analizarChecksum { bytes -> bytes.reduce { actual, valor -> actual xor valor } }

        assertEquals(ClaseByte.CHECKSUM, resultado.clase)
        assertEquals("xor", resultado.detalle)
    }

    @Test
    fun `detecta checksum de suma`() {
        val resultado = analizarChecksum { bytes -> bytes.sum() and 0xFF }

        assertEquals(ClaseByte.CHECKSUM, resultado.clase)
        assertEquals("suma", resultado.detalle)
    }

    @Test
    fun `detecta checksum crc8 1d init ff xorout ff`() {
        val conocido = crc8J1850(listOf(0x01, 0x02, 0x03))
        assertEquals(0xC1, conocido)
        val frames = List(8) { indice ->
            val bytes = listOf(indice + 1, 0x02, 0x03)
            bytes + crc8J1850(bytes)
        }

        val resultado = AnalisisBitsCan.analizar(tramas(frames)).last()

        assertEquals(ClaseByte.CHECKSUM, resultado.clase)
        assertEquals("crc8 0x1D init FF xorout FF", resultado.detalle)
    }

    @Test
    fun `menos de ocho tramas no se clasifican como contador ni checksum`() {
        val resultado = AnalisisBitsCan.analizar(
            tramas((0..6).map { listOf(it, it xor 0xFF) }),
        )

        assertTrue(resultado.all { it.clase != ClaseByte.CONTADOR && it.clase != ClaseByte.CHECKSUM })
    }

    @Test
    fun `clasifica senal y lista sus bits variables`() {
        val resultado = AnalisisBitsCan.analizar(
            tramas(listOf(0x00, 0x80, 0x40, 0xC0, 0x01, 0x81, 0x41, 0xC1).map { listOf(it) }),
        ).single()

        assertEquals(ClaseByte.SENAL, resultado.clase)
        assertEquals(listOf(0, 6, 7), resultado.bitsQueCambian)
    }

    private fun analizarChecksum(checksum: (List<Int>) -> Int): AnalisisByte {
        val frames = List(8) { indice ->
            val datos = listOf(indice, indice * 3 and 0xFF)
            datos + checksum(datos)
        }
        return AnalisisBitsCan.analizar(tramas(frames)).last()
    }

    private fun tramas(valores: List<List<Int>>) =
        valores.map { TramaCan(0L, 0x123, false, it) }

    private fun crc8J1850(bytes: List<Int>): Int {
        var crc = 0xFF
        bytes.forEach { byte ->
            crc = crc xor byte
            repeat(8) {
                crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x1D) and 0xFF
                else (crc shl 1) and 0xFF
            }
        }
        return crc xor 0xFF
    }
}
