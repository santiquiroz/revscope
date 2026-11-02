package com.revscope.core.obd.perfilcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutorFuentesTest {

    private var reloj = 1_000L

    private fun resolutor(
        estado: EstadoSenal = EstadoSenal.VERIFICADA,
        modo: ModoFuente = ModoFuente.COMBINADO,
        preferencias: Map<String, FuenteSenal> = emptyMap(),
        esCan: Boolean = true,
        intervaloVentanaMs: Long = 0,
    ) = ResolutorFuentes(perfilDePrueba(estado), modo, preferencias, esCan, intervaloVentanaMs) { reloj }

    @Test
    fun `señal sin_verificar nunca usa bus`() {
        val r = resolutor(estado = EstadoSenal.SIN_VERIFICAR)
        r.registrarTrama(ClavesSenal.GEAR)

        assertFalse(r.usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `verificada y fresca usa bus`() {
        val r = resolutor()
        r.registrarTrama(ClavesSenal.GEAR)

        assertTrue(r.usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `preferencia PID gana sobre bus`() {
        val r = resolutor(preferencias = mapOf(ClavesSenal.GEAR to FuenteSenal.PID))
        r.registrarTrama(ClavesSenal.GEAR)

        assertFalse(r.usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `modo ESTANDAR nunca usa bus`() {
        val r = resolutor(modo = ModoFuente.ESTANDAR)
        r.registrarTrama(ClavesSenal.GEAR)

        assertFalse(r.usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `sin perfil el modo efectivo es ESTANDAR`() {
        assertEquals(ModoFuente.ESTANDAR, ModoFuente.SOLO_BUS.efectivo(null))
    }

    @Test
    fun `protocolo no CAN nunca usa bus`() {
        val r = resolutor(esCan = false)
        r.registrarTrama(ClavesSenal.GEAR)

        assertFalse(r.usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `sin registrar no hay bus`() {
        assertFalse(resolutor().usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `sin tramas en 3 periodos caduca y se reporta una sola vez`() {
        val r = resolutor()
        r.registrarTrama(ClavesSenal.GEAR)

        reloj += 300
        assertTrue(r.usaBus(ClavesSenal.GEAR))
        assertTrue(r.caducadas().isEmpty())

        reloj += 1
        assertFalse(r.usaBus(ClavesSenal.GEAR))
        assertEquals(setOf(ClavesSenal.GEAR), r.caducadas())
        assertTrue(r.caducadas().isEmpty())
    }

    @Test
    fun `la ventana del modo combinado alarga la frescura`() {
        val r = resolutor(intervaloVentanaMs = 500)
        r.registrarTrama(ClavesSenal.GEAR)

        reloj += 1_500
        assertTrue(r.usaBus(ClavesSenal.GEAR))
        reloj += 1
        assertFalse(r.usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `vuelve a usar bus cuando llegan tramas de nuevo`() {
        val r = resolutor()
        r.registrarTrama(ClavesSenal.GEAR)
        reloj += 1_000
        assertFalse(r.usaBus(ClavesSenal.GEAR))

        r.registrarTrama(ClavesSenal.GEAR)

        assertTrue(r.usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `preferencias se leen del json y lo desconocido se ignora`() {
        val fuentes = fuentesPorSenalDe("""{"GEAR":"CALCULADA","0D":"GPS","0C":"RARO"}""")

        assertEquals(mapOf("GEAR" to FuenteSenal.CALCULADA, "0D" to FuenteSenal.GPS), fuentes)
        assertTrue(fuentesPorSenalDe("no es json").isEmpty())
        assertEquals(ModoFuente.ESTANDAR, modoFuenteDe("OTRO"))
        assertEquals(ModoFuente.SOLO_BUS, modoFuenteDe("SOLO_BUS"))
    }
}

internal fun senalDePrueba(
    clave: String = ClavesSenal.GEAR,
    id: String = "500",
    bitInicio: Int = 32,
    largo: Int = 8,
    tipo: TipoSenal = TipoSenal.ENUM,
    estado: EstadoSenal = EstadoSenal.VERIFICADA,
    periodoMs: Long = 100,
    invalidos: Set<Long> = setOf(7L),
) = SenalCan(
    clave = clave,
    nombre = "Marcha",
    id = id,
    bitInicio = bitInicio,
    largo = largo,
    orden = OrdenBytes.BIG,
    firmado = false,
    factor = 1.0,
    offset = 0.0,
    unidad = "",
    tipo = tipo,
    valores = mapOf(0L to "N", 1L to "1", 2L to "2", 3L to "3", 4L to "4", 5L to "5"),
    invalidos = invalidos,
    periodoMs = periodoMs,
    estado = estado,
    origen = "manual",
)

internal fun perfilDePrueba(
    estado: EstadoSenal = EstadoSenal.VERIFICADA,
    senales: List<SenalCan> = listOf(senalDePrueba(estado = estado)),
) = PerfilCan(
    version = 1,
    id = "prueba.moto",
    vehiculo = VehiculoPerfil("TVS", "Apache", "2024", "MOTORCYCLE"),
    calibracionesEcu = emptyList(),
    protocolo = "6",
    licencia = "CC-BY-SA-4.0",
    autor = "prueba",
    atribucion = "prueba",
    senales = senales,
)
