package com.revscope.core.obd.workshop

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.protocol.ReadinessParser
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthReportFormatoTest {

    private val items = listOf(
        DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.FALLA, "DTC", "1 códigos: P0122", "Ábrelos en Códigos de falla"),
        DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.OK, "Eléctrico", "Carga correcta", "14,2 V con el motor encendido"),
    )

    private val metricas = MetricasChequeo(
        stft = -6.2,
        ltft = 2.3,
        voltaje = 14.2,
        motorEncendido = true,
        ect = 79.0,
        monitoresCompletos = 3,
        monitoresTotales = 3,
        dtcs = listOf("P0122"),
    )

    @Test
    fun `el formato v1 (array de hallazgos) se sigue leyendo sin métricas`() {
        val v1 = """[{"area":"DTC","nivel":"OK","titulo":"Sin códigos de falla","causa":"Memoria de fallas limpia"}]"""

        val leido = HealthReportFormato.leer(v1)

        assertEquals(1, leido.version)
        assertEquals(
            listOf(DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.OK, "DTC", "Sin códigos de falla", "Memoria de fallas limpia")),
            leido.items,
        )
        assertNull(leido.metricas)
    }

    @Test
    fun `el formato v2 guarda hallazgos y métricas y se lee igual`() {
        val json = HealthReportFormato.escribir(items, metricas)

        val leido = HealthReportFormato.leer(json)

        assertEquals(2, leido.version)
        assertEquals(items, leido.items)
        assertEquals(metricas, leido.metricas)
    }

    @Test
    fun `el v2 es un objeto con la versión, los hallazgos y las métricas con nombres estables`() {
        val json = JSONObject(HealthReportFormato.escribir(items, metricas))

        assertEquals(2, json.getInt("v"))
        assertEquals("P0122", json.getJSONArray("items").getJSONObject(0).getString("titulo").substringAfter(": "))
        val m = json.getJSONObject("metricas")
        assertEquals(-6.2, m.getDouble("stft"), 1e-9)
        assertEquals(2.3, m.getDouble("ltft"), 1e-9)
        assertEquals(-3.9, m.getDouble("correccionTotal"), 1e-9)
        assertEquals(14.2, m.getDouble("voltaje"), 1e-9)
        assertEquals(79.0, m.getDouble("ect"), 1e-9)
        assertEquals(3, m.getInt("monitoresCompletos"))
        assertEquals(3, m.getInt("monitoresTotales"))
        assertEquals("P0122", m.getJSONArray("dtcs").getString(0))
    }

    @Test
    fun `las métricas ausentes no se escriben y se leen como nulas`() {
        val json = HealthReportFormato.escribir(items, MetricasChequeo(voltaje = 12.2, motorEncendido = false))

        val m = JSONObject(json).getJSONObject("metricas")
        assertFalse(m.has("stft"))
        assertFalse(m.has("correccionTotal"))
        val leido = HealthReportFormato.leer(json).metricas!!
        assertNull(leido.ltft)
        assertNull(leido.correccionTotal)
        assertEquals(12.2, leido.voltaje!!, 1e-9)
    }

    @Test
    fun `un JSON dañado se lee como un chequeo vacío sin lanzar`() {
        val leido = HealthReportFormato.leer("{no es json")

        assertTrue(leido.items.isEmpty())
        assertNull(leido.metricas)
    }

    @Test
    fun `un hallazgo con nivel desconocido se descarta sin perder los demás`() {
        val v1 = """[{"area":"X","nivel":"RARO","titulo":"t","causa":"c"},""" +
            """{"area":"DTC","nivel":"OK","titulo":"Sin códigos","causa":"Limpia"}]"""

        assertEquals(listOf("Sin códigos"), HealthReportFormato.leer(v1).items.map { it.titulo })
    }

    @Test
    fun `la corrección total es la suma de los ajustes corto y largo`() {
        assertEquals(-3.9, MetricasChequeo(stft = -6.2, ltft = 2.3).correccionTotal!!, 1e-9)
        assertNull(MetricasChequeo(ltft = 2.3).correccionTotal)
    }

    @Test
    fun `las métricas salen de las lecturas, el readiness y los códigos del chequeo`() {
        val lecturas = mapOf(
            "06" to ObdReading("06", -6.2, "%"),
            "07" to ObdReading("07", 2.3, "%"),
            "0C" to ObdReading("0C", 1_500.0, "rpm"),
            "05" to ObdReading("05", 79.0, "°C"),
            ObdSessionManager.VBAT_PID to ObdReading(ObdSessionManager.VBAT_PID, 14.2, "V"),
        )
        val readiness = ReadinessParser.ReadinessStatus(
            milOn = true,
            dtcCount = 1,
            isDiesel = false,
            monitors = listOf(
                ReadinessParser.MonitorResult("Encendido (misfire)", soportado = true, completo = true),
                ReadinessParser.MonitorResult("Sistema de combustible", soportado = true, completo = false),
                ReadinessParser.MonitorResult("Catalizador", soportado = false, completo = true),
            ),
        )

        val m = MetricasChequeo.desde(lecturas, readiness, dtcs = listOf("P0122"))

        assertEquals(MetricasChequeo(-6.2, 2.3, 14.2, true, 79.0, 1, 2, listOf("P0122")), m)
    }

    @Test
    fun `si la lectura de códigos falló las métricas lo dicen en vez de fingir que no hay códigos`() {
        val m = MetricasChequeo.desde(emptyMap(), readiness = null, dtcs = null)

        assertFalse(m.dtcsLeidos)
        assertTrue(m.dtcs.isEmpty())
        assertNull(m.motorEncendido)
        assertFalse(HealthReportFormato.leer(HealthReportFormato.escribir(emptyList(), m)).metricas!!.dtcsLeidos)
    }
}
