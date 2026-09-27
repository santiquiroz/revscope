package com.revscope.core.obd.taller.informe

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.sesion.AnalisisSesion
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArmadorInformeBandasTest {

    @Test
    fun `una banda de un solo lado ignora los numeros del nombre de la fuente`() {
        val medicion = medicionCon("≥ 4,80 V · Fuente: Manual TNT 150i p. 45")

        assertEquals(4.8, medicion.bandaMin!!, 1e-9)
        assertNull(medicion.bandaMax)
    }

    @Test
    fun `un tope maximo con fuente numerada conserva solo el maximo`() {
        val medicion = medicionCon("≤ 0,10 V · Fuente: Hoja técnica 2019")

        assertNull(medicion.bandaMin)
        assertEquals(0.1, medicion.bandaMax!!, 1e-9)
    }

    @Test
    fun `un rango completo tipico se lee igual que antes`() {
        val medicion = medicionCon("0,4–0,8 V · Típico (editable)")

        assertEquals(0.4, medicion.bandaMin!!, 1e-9)
        assertEquals(0.8, medicion.bandaMax!!, 1e-9)
    }

    @Test
    fun `una referencia sin banda no inventa limites`() {
        val medicion = medicionCon("Sin banda de referencia")

        assertNull(medicion.bandaMin)
        assertNull(medicion.bandaMax)
    }

    private fun medicionCon(referencia: String): MedicionInforme {
        val sesion = SesionTaller(vehiculoId = 7, inicio = 1_000, cierre = 9_000, titulo = "Batería")
        val evento = EventoTaller(
            id = 1,
            sesionId = 1,
            instante = 2_000,
            tipo = TipoEvento.MEDICION_MULTIMETRO,
            titulo = "Multímetro · Batería",
            payloadJson = payload(referencia).toString(),
        )
        val informe = ArmadorInformeTaller().armar(
            sesion = sesion,
            eventos = listOf(evento),
            vehiculo = VehiculoTaller(7, "Benelli TNT 150i", null, VehicleType.MOTORCYCLE),
            analisis = AnalisisSesion(emptyList(), null, null, emptyList(), emptyList()),
            conocimiento = null,
            guia = null,
            versionApp = "1.21.0",
        )
        return informe.mediciones.single()
    }

    private fun payload(referencia: String) = JSONObject()
        .put("titulo", "Batería")
        .put("unidad", "V")
        .put(
            "lecturas",
            JSONArray().put(
                JSONObject()
                    .put("funcion", "ALIMENTACION")
                    .put("condicion", "CONTACTO")
                    .put("valor", 5.0)
                    .put("unidad", "V")
                    .put("estado", "DENTRO")
                    .put("referencia", referencia),
            ),
        )
}
