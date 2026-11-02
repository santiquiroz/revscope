package com.revscope.core.obd.perfilcan

import com.revscope.core.obd.model.ObdReading
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltroFuentesTest {

    private var reloj = 1_000L

    private val rpm = senalDePrueba(clave = ClavesSenal.RPM, id = "300", bitInicio = 8, largo = 16, tipo = TipoSenal.NUMERO, periodoMs = 50)
    private val pata = senalDePrueba(clave = ClavesSenal.PATA_LATERAL, bitInicio = 46, largo = 1, tipo = TipoSenal.BANDERA)

    private fun resolutor(preferencias: Map<String, FuenteSenal> = emptyMap()) = ResolutorFuentes(
        perfilDePrueba(senales = listOf(senalDePrueba(), rpm, pata)),
        ModoFuente.COMBINADO,
        preferencias,
        esCan = true,
        intervaloVentanaMs = 0,
    ) { reloj }

    private fun lectura(clave: String) = ObdReading(clave, 1.0, "", reloj)

    @Test
    fun `pid 0C se descarta cuando el bus es la fuente`() {
        val res = resolutor()
        res.registrarTrama(ClavesSenal.RPM)

        assertFalse(FiltroFuentes.aceptaPid(lectura(ClavesSenal.RPM), res))
        assertTrue(FiltroFuentes.aceptaPid(lectura(ClavesSenal.VELOCIDAD), res))
    }

    @Test
    fun `GEAR derivada se descarta con GEAR de bus fresca`() {
        val res = resolutor()
        res.registrarTrama(ClavesSenal.GEAR)

        assertFalse(FiltroFuentes.aceptaDerivada(lectura(ClavesSenal.GEAR), res))
        assertTrue(FiltroFuentes.aceptaBus(lectura(ClavesSenal.GEAR), res))
    }

    @Test
    fun `GEAR derivada vuelve cuando la del bus caduca`() {
        val res = resolutor()
        res.registrarTrama(ClavesSenal.GEAR)

        reloj += 301

        assertTrue(FiltroFuentes.aceptaDerivada(lectura(ClavesSenal.GEAR), res))
        assertFalse(FiltroFuentes.aceptaBus(lectura(ClavesSenal.GEAR), res))
    }

    @Test
    fun `lectura de bus de señal no elegida se descarta`() {
        val res = resolutor(mapOf(ClavesSenal.GEAR to FuenteSenal.CALCULADA))
        res.registrarTrama(ClavesSenal.GEAR)

        assertFalse(FiltroFuentes.aceptaBus(lectura(ClavesSenal.GEAR), res))
        assertTrue(FiltroFuentes.aceptaDerivada(lectura(ClavesSenal.GEAR), res))
    }
}
