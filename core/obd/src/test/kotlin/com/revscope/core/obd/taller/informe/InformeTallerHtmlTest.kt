package com.revscope.core.obd.taller.informe

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.dtc.GuiaDtc
import com.revscope.core.obd.taller.dtc.UrgenciaDtc
import com.revscope.core.obd.taller.dtc.VerificacionDtc
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.modelo.NotaModelo
import com.revscope.core.obd.taller.modelo.RepuestoModelo
import com.revscope.core.obd.taller.modelo.TipoNota
import com.revscope.core.obd.taller.modelo.TipoRepuesto
import com.revscope.core.obd.taller.sesion.AnalisisSesion
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InformeTallerHtmlTest {

    @Test
    fun `genera las diez secciones del caso Benelli en orden`() {
        val html = InformeTallerHtml.render(informeBenelli())
        val titulos = listOf(
            "Informe de taller",
            "Conclusión",
            "Medición con multímetro",
            "Lo que registró la ECU",
            "Comparación con el chequeo anterior",
            "Interpretación",
            "¿Es el repuesto correcto?",
            "Qué revisar, en orden",
            "Pendiente por confirmar",
            "Fuentes",
        )

        titulos.zipWithNext().forEach { (anterior, siguiente) ->
            assertTrue("$anterior debe preceder a $siguiente", html.indexOf(anterior) < html.indexOf(siguiente))
        }
        assertTrue(Regex("<section\\b").findAll(html).count() == 10)
        assertTrue(html.contains("P0122"))
        assertTrue(html.contains("9,2 %"))
        assertTrue(html.contains("≈0,46 V"))
        assertTrue(html.contains("Catálogo de partes Auteco"))
        assertTrue(Regex("Típico").findAll(html).count() >= 5)
    }

    @Test
    fun `escapa todo texto del usuario y no carga recursos externos`() {
        val html = InformeTallerHtml.render(informeBenelli("<script>alert('x')</script>"))

        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"))
        val urlsFueraDeEnlaces = Regex("(?i)(?:src|action|poster)\\s*=\\s*[\"']https?://").findAll(html).toList()
        assertTrue(urlsFueraDeEnlaces.isEmpty())
        assertFalse(Regex("(?i)url\\s*\\(\\s*[\"']?https?://").containsMatchIn(html))
        assertTrue(html.contains("<a href=\"https://www.auteco.com.co/catalogo\""))
    }

    @Test
    fun `permanece por debajo de 300 KB con tres pruebas`() {
        val base = informeBenelli()
        val informe = base.copy(pruebas = List(3) { base.pruebas.single().copy(titulo = "Prueba ${it + 1}") })

        val bytes = InformeTallerHtml.render(informe).toByteArray(Charsets.UTF_8).size

        assertTrue("El HTML ocupa $bytes bytes", bytes < 300 * 1024)
    }

    private fun informeBenelli(interpretacion: String = "La señal baja es compatible con P0122"): InformeTaller {
        val sesion = SesionTaller(
            vehiculoId = 7,
            claveModelo = "benelli-tnt-150i",
            inicio = 1_727_438_400_000,
            cierre = 1_727_442_000_000,
            titulo = "Señal del TPS fuera de rango",
            sintomas = setOf(Sintoma.SE_AHOGA_AL_ACELERAR, Sintoma.MIL_ENCENDIDA),
            sintomasTexto = "Pierde respuesta <al abrir>",
            interpretacion = interpretacion,
        )
        val eventos = listOf(
            EventoTaller(
                id = 1,
                sesionId = 1,
                instante = sesion.inicio + 1_000,
                tipo = TipoEvento.DTC_LECTURA,
                titulo = "Lectura de códigos",
                resumen = "Activo: P0122",
                veredicto = Veredicto.FALLA,
            ),
            EventoTaller(
                id = 2,
                sesionId = 1,
                instante = sesion.inicio + 2_000,
                tipo = TipoEvento.MEDICION_MULTIMETRO,
                titulo = "Multímetro · TPS",
                resumen = "Señal baja",
                veredicto = Veredicto.FALLA,
                payloadJson = medicionJson().toString(),
            ),
            EventoTaller(
                id = 3,
                sesionId = 1,
                instante = sesion.inicio + 3_000,
                tipo = TipoEvento.PRUEBA_GUIADA,
                titulo = "Barrido del TPS",
                resumen = "Señal baja todo el recorrido",
                veredicto = Veredicto.FALLA,
                payloadJson = pruebaJson().toString(),
            ),
        )
        val conocimiento = ConocimientoModelo(
            clave = "benelli-tnt-150i",
            nombre = "Benelli TNT 150i",
            tipo = VehicleType.MOTORCYCLE,
            ecu = "Delphi MT05",
            fuenteEcu = "Manual de servicio",
            notasProtocolo = "ISO 14230",
            notas = listOf(
                NotaModelo(TipoNota.PENDIENTE, "Confirmar referencia impresa", "Inspección del vehículo"),
                NotaModelo(TipoNota.FUENTE, "Catálogo del modelo", "Auteco", "https://www.auteco.com.co/catalogo"),
            ),
            cableado = emptyList(),
            repuestos = listOf(
                RepuestoModelo(
                    rol = "TPS",
                    referencia = "280756030001",
                    descripcion = "Cuerpo de aceleración",
                    tipo = TipoRepuesto.OEM,
                    nota = "Verificar número de chasis",
                    fuente = "Catálogo de partes Auteco",
                ),
            ),
            bandas = emptyList(),
        )
        val guia = GuiaDtc(
            codigo = "P0122",
            titulo = "Entrada baja del circuito TPS",
            sistema = "Admisión",
            urgencia = UrgenciaDtc.REVISAR_PRONTO,
            causas = listOf("Señal en corto a masa"),
            verificaciones = listOf(
                VerificacionDtc("Inspeccionar conector", "Buscar corrosión", null),
                VerificacionDtc("Comprobar continuidad", "Medir el cable de señal", null),
            ),
            notasMoto = emptyList(),
            relacionados = emptyList(),
        )
        return ArmadorInformeTaller().armar(
            sesion = sesion,
            eventos = eventos,
            vehiculo = VehiculoTaller(7, "Benelli TNT 150i", "benelli-tnt-150i", VehicleType.MOTORCYCLE),
            analisis = AnalisisSesion(listOf("P0122"), null, null, emptyList(), emptyList()),
            conocimiento = conocimiento,
            guia = guia,
            versionApp = "1.21.0",
        )
    }

    private fun medicionJson() = JSONObject()
        .put("plantilla", "TPS")
        .put("titulo", "Sensor TPS")
        .put("unidad", "V")
        .put(
            "lecturas",
            JSONArray()
                .put(lectura("REFERENCIA", "CONTACTO", 5.0, "4,8–5,2 V · Típico (editable)", "DENTRO"))
                .put(lectura("MASA", "CONTACTO", 0.0, "0–0,1 V · Típico (editable)", "DENTRO"))
                .put(lectura("SENAL", "CERRADO", 0.10, "0,4–0,8 V · Típico (editable)", "BAJO"))
                .put(lectura("SENAL", "MEDIO", 0.35, "2,0–3,0 V · Típico (editable)", "BAJO"))
                .put(lectura("SENAL", "FONDO", 0.80, "4,0–4,8 V · Típico (editable)", "BAJO")),
        )

    private fun lectura(funcion: String, condicion: String, valor: Double, referencia: String, estado: String) = JSONObject()
        .put("funcion", funcion)
        .put("color", "azul")
        .put("condicion", condicion)
        .put("valor", valor)
        .put("unidad", "V")
        .put("estado", estado)
        .put("referencia", referencia)

    private fun pruebaJson() = JSONObject()
        .put("estado", "TERMINADA")
        .put("prueba", "TPS_BARRIDO")
        .put("tituloPrueba", "Barrido del TPS")
        .put("veredicto", "FALLA")
        .put("titulo", "Señal baja todo el recorrido")
        .put("interpretacion", "Compatible con una señal baja")
        .put("hallazgos", JSONArray().put("No alcanzó el rango esperado"))
        .put(
            "detalle",
            JSONObject()
                .put("vref", JSONObject().put("voltios", 5.0).put("origen", "Típico (editable)"))
                .put(
                    "pasos",
                    JSONArray()
                        .put(JSONObject().put("clave", "CERRADO").put("mediaPct", 2.35).put("mediaV", 0.12))
                        .put(JSONObject().put("clave", "MEDIO").put("mediaPct", 9.2).put("mediaV", 0.46))
                        .put(JSONObject().put("clave", "FONDO").put("mediaPct", 17.8).put("mediaV", 0.89))
                        .put(JSONObject().put("clave", "CERRADO_FINAL").put("mediaPct", 2.75).put("mediaV", 0.14)),
                )
        )
        .put(
            "segmentos",
            JSONArray()
                .put(JSONObject().put("clave", "CERRADO").put("inicio_ms", 0).put("fin_ms", 100))
                .put(JSONObject().put("clave", "MEDIO").put("inicio_ms", 100).put("fin_ms", 200))
                .put(JSONObject().put("clave", "FONDO").put("inicio_ms", 200).put("fin_ms", 300)),
        )
        .put(
            "serie",
            JSONObject().put(
                "11",
                JSONObject()
                    .put("t_ms", JSONArray(listOf(0, 100, 200, 300)))
                    .put("v", JSONArray(listOf(4.4, 12.0, 25.0, 63.0)))
                    .put("n", 4),
            ),
        )
}
