package com.revscope.core.obd.perfilcan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecPerfilCanTest {

    private fun senal(
        clave: String = ClavesSenal.GEAR,
        id: String = "500",
        bitInicio: Int = 32,
        largo: Int = 8,
        tipo: TipoSenal = TipoSenal.ENUM,
    ) = SenalCan(
        clave = clave,
        nombre = "Señal $clave",
        id = id,
        bitInicio = bitInicio,
        largo = largo,
        orden = OrdenBytes.BIG,
        firmado = false,
        factor = 1.0,
        offset = 0.0,
        unidad = "",
        tipo = tipo,
        valores = emptyMap(),
        invalidos = emptySet(),
        periodoMs = 100,
        estado = EstadoSenal.SIN_VERIFICAR,
        origen = "manual",
    )

    private fun perfil(senales: List<SenalCan> = listOf(senal())) = PerfilCan(
        version = 1,
        id = "tvs.apache_rtr_160_4v.2024",
        vehiculo = VehiculoPerfil("TVS", "Apache RTR 160 4V", "2024", "MOTORCYCLE"),
        calibracionesEcu = listOf("U507EBS6B1A1a706"),
        protocolo = "6",
        licencia = "CC-BY-SA-4.0",
        autor = "santiquiroz",
        atribucion = "Mapeado con RevScope",
        senales = senales,
    )

    private fun jsonCon(cambio: JSONObject.() -> Unit): String =
        JSONObject(CodecPerfilCan.escribir(perfil())).apply(cambio).toString()

    private fun jsonConSenal(cambio: JSONObject.() -> Unit): String = jsonCon {
        getJSONArray("senales").getJSONObject(0).cambio()
    }

    private fun assertFallaPor(json: String, campo: String) {
        val r = CodecPerfilCan.leer(json)
        assertTrue(r.isFailure)
        val mensaje = r.exceptionOrNull()!!.message!!
        assertTrue("'$mensaje' no menciona $campo", mensaje.contains(campo))
    }

    @Test
    fun `ida y vuelta conserva todos los campos`() {
        val original = perfil(
            listOf(
                senal().copy(
                    valores = mapOf(0L to "N", 1L to "1", 2L to "2"),
                    invalidos = setOf(7L),
                    estado = EstadoSenal.VERIFICADA,
                    origen = "asistente",
                ),
                senal(ClavesSenal.RPM, "300", 8, 16, TipoSenal.NUMERO).copy(
                    orden = OrdenBytes.LITTLE,
                    firmado = true,
                    factor = 0.25,
                    offset = -40.0,
                    unidad = "rpm",
                    periodoMs = 50,
                    estado = EstadoSenal.APRENDIDA,
                ),
                senal(ClavesSenal.PATA_LATERAL, "18FF0A01", 46, 1, TipoSenal.BANDERA),
            ),
        )

        val leido = CodecPerfilCan.leer(CodecPerfilCan.escribir(original)).getOrThrow()

        assertEquals(original, leido)
    }

    @Test
    fun `escribe enums en minusculas`() {
        val json = JSONObject(CodecPerfilCan.escribir(perfil()))
        val s = json.getJSONArray("senales").getJSONObject(0)

        assertEquals("big", s.getString("orden"))
        assertEquals("enum", s.getString("tipo"))
        assertEquals("sin_verificar", s.getString("estado"))
    }

    @Test
    fun `rechaza version distinta de 1`() {
        assertFallaPor(jsonCon { put("version", 2) }, "version")
    }

    @Test
    fun `rechaza id CAN no hexadecimal de 3 u 8 digitos`() {
        assertFallaPor(jsonConSenal { put("id", "50G") }, "id")
        assertFallaPor(jsonConSenal { put("id", "5000") }, "id")
    }

    @Test
    fun `rechaza bitInicio mas largo fuera de 0 a 63`() {
        assertFallaPor(jsonConSenal { put("bitInicio", 60) }, "bitInicio")
        assertFallaPor(jsonConSenal { put("bitInicio", -1) }, "bitInicio")
    }

    @Test
    fun `rechaza clave desconocida`() {
        assertFallaPor(jsonConSenal { put("clave", "TURBO") }, "clave")
    }

    @Test
    fun `rechaza json de mas de 64 KB`() {
        assertFallaPor(jsonCon { put("relleno", "x".repeat(65 * 1024)) }, "tamaño")
    }

    @Test
    fun `rechaza campo vin`() {
        assertFallaPor(jsonCon { put("vin", "9FB000000000000") }, "vin")
        assertFallaPor(jsonCon { getJSONObject("vehiculo").put("vin", "9FB000000000000") }, "vin")
    }

    @Test
    fun `un perfil con una senal invalida falla entero`() {
        val json = CodecPerfilCan.escribir(perfil(listOf(senal(), senal(ClavesSenal.RPM, "ZZZ"))))

        assertTrue(CodecPerfilCan.leer(json).isFailure)
    }

    @Test
    fun `campos opcionales ausentes toman defaults`() {
        val json = jsonConSenal {
            remove("invalidos")
            remove("valores")
            remove("estado")
        }

        val s = CodecPerfilCan.leer(json).getOrThrow().senales.single()

        assertTrue(s.invalidos.isEmpty())
        assertTrue(s.valores.isEmpty())
        assertEquals(EstadoSenal.SIN_VERIFICAR, s.estado)
    }
}
