package com.revscope.core.obd.perfilcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecodificadorSenalTest {

    private fun hex(texto: String): ByteArray =
        texto.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun senal(
        bitInicio: Int,
        largo: Int = 8,
        tipo: TipoSenal = TipoSenal.NUMERO,
        factor: Double = 1.0,
        offset: Double = 0.0,
        firmado: Boolean = false,
        valores: Map<Long, String> = emptyMap(),
        invalidos: Set<Long> = emptySet(),
    ) = SenalCan(
        clave = ClavesSenal.GEAR,
        nombre = "prueba",
        id = "500",
        bitInicio = bitInicio,
        largo = largo,
        orden = OrdenBytes.BIG,
        firmado = firmado,
        factor = factor,
        offset = offset,
        unidad = "",
        tipo = tipo,
        valores = valores,
        invalidos = invalidos,
        periodoMs = 100,
        estado = EstadoSenal.VERIFICADA,
        origen = "manual",
    )

    private val marcha = senal(
        bitInicio = 32,
        tipo = TipoSenal.ENUM,
        valores = mapOf(0L to "N", 1L to "1", 2L to "2", 3L to "3", 4L to "4", 5L to "5"),
        invalidos = setOf(7L),
    )

    private fun bandera(bit: Int) = senal(bitInicio = bit, largo = 1, tipo = TipoSenal.BANDERA)

    @Test
    fun `marcha neutro`() {
        assertEquals(0.0, DecodificadorSenal.valor(marcha, hex("0000450000004604"))!!, 0.0)
        assertEquals("N", DecodificadorSenal.texto(marcha, hex("0000450000004604")))
    }

    @Test
    fun `marcha tercera`() {
        assertEquals(3.0, DecodificadorSenal.valor(marcha, hex("0000440003004B04"))!!, 0.0)
        assertEquals("3", DecodificadorSenal.texto(marcha, hex("0000440003004B04")))
    }

    @Test
    fun `marcha 07 entre marchas da null`() {
        assertNull(DecodificadorSenal.valor(marcha, hex("0000440007004B04")))
        assertNull(DecodificadorSenal.texto(marcha, hex("0000440007004B04")))
    }

    @Test
    fun `pata lateral bit 46`() {
        assertEquals(1.0, DecodificadorSenal.valor(bandera(46), hex("0000450000404604"))!!, 0.0)
        assertEquals(0.0, DecodificadorSenal.valor(bandera(46), hex("0000450000004604"))!!, 0.0)
        assertEquals("Sí", DecodificadorSenal.texto(bandera(46), hex("0000450000404604")))
        assertEquals("No", DecodificadorSenal.texto(bandera(46), hex("0000450000004604")))
    }

    @Test
    fun `kill switch bit 58 y motor en marcha bit 59`() {
        assertEquals(1.0, DecodificadorSenal.valor(bandera(58), hex("0000450000004604"))!!, 0.0)
        assertEquals(0.0, DecodificadorSenal.valor(bandera(58), hex("0000450000004600"))!!, 0.0)
        assertEquals(1.0, DecodificadorSenal.valor(bandera(59), hex("0000450000004D0C"))!!, 0.0)
        assertEquals(0.0, DecodificadorSenal.valor(bandera(59), hex("0000450000004604"))!!, 0.0)
    }

    @Test
    fun `rpm big de 16 bits y acelerador`() {
        val trama = hex("0C0DF8000000D200")

        assertEquals(3576.0, DecodificadorSenal.valor(senal(bitInicio = 8, largo = 16), trama)!!, 0.0)
        assertEquals(6.0, DecodificadorSenal.valor(senal(bitInicio = 0, factor = 0.5), trama)!!, 1e-9)
    }

    @Test
    fun `temperaturas de motor y aire con offset`() {
        val trama = hex("00005C000000540C")

        assertEquals(52.0, DecodificadorSenal.valor(senal(bitInicio = 16, offset = -40.0), trama)!!, 0.0)
        assertEquals(44.0, DecodificadorSenal.valor(senal(bitInicio = 48, offset = -40.0), trama)!!, 0.0)
    }

    @Test
    fun `embrague bit 24`() {
        assertEquals(1.0, DecodificadorSenal.valor(bandera(24), hex("000000010000D340"))!!, 0.0)
        assertEquals(0.0, DecodificadorSenal.valor(bandera(24), hex("0C0DF8000000D200"))!!, 0.0)
    }

    @Test
    fun `velocidad de rueda`() {
        val rueda = senal(bitInicio = 0, largo = 16, factor = 0.01)

        assertEquals(5.76, DecodificadorSenal.valor(rueda, hex("0240FFFF02010000"))!!, 1e-9)
    }

    @Test
    fun `little endian invierte los bytes`() {
        val rueda = senal(bitInicio = 0, largo = 16).copy(orden = OrdenBytes.LITTLE)

        assertEquals(0x4002.toDouble(), DecodificadorSenal.valor(rueda, hex("0240FFFF02010000"))!!, 0.0)
    }

    @Test
    fun `trama de 4 bytes para campo en byte 6 da null`() {
        val aire = senal(bitInicio = 48, offset = -40.0)

        assertNull(DecodificadorSenal.valor(aire, hex("00005C00")))
        assertNull(DecodificadorSenal.texto(aire, hex("00005C00")))
    }

    @Test
    fun `campo de 16 bits que se sale de la trama da null`() {
        assertNull(DecodificadorSenal.valor(senal(bitInicio = 8, largo = 16), hex("0C0D")))
    }

    @Test
    fun `campo firmado negativo`() {
        assertEquals(-1.0, DecodificadorSenal.valor(senal(bitInicio = 0, firmado = true), hex("FF"))!!, 0.0)
        assertEquals(127.0, DecodificadorSenal.valor(senal(bitInicio = 0, firmado = true), hex("7F"))!!, 0.0)
    }
}
