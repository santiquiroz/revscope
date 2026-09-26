package com.revscope.core.obd.mcp

import com.revscope.core.data.db.dao.HealthReportDao
import com.revscope.core.data.db.entities.HealthReportEntity
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.obd.workshop.HealthReportFormato
import com.revscope.core.obd.workshop.MetricasChequeo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetChequeoSaludToolTest {

    private fun tool(json: String?): GetChequeoSaludTool {
        val dao = mockk<HealthReportDao>()
        coEvery { dao.latest() } returns json?.let { HealthReportEntity(id = 4, vehicleProfileId = 7, timestamp = 99, resultsJson = it) }
        return GetChequeoSaludTool(dao)
    }

    @Test
    fun `un chequeo v1 devuelve sus hallazgos y métricas nulas`() = runTest {
        val v1 = """[{"area":"DTC","nivel":"OK","titulo":"Sin códigos de falla","causa":"Memoria de fallas limpia"}]"""

        val respuesta = JSONObject(tool(v1).call(JSONObject()))

        assertEquals(1, respuesta.getInt("formato"))
        assertEquals("OK", respuesta.getJSONArray("items").getJSONObject(0).getString("nivel"))
        assertTrue(respuesta.isNull("metricas"))
    }

    @Test
    fun `un chequeo v2 devuelve también las métricas`() = runTest {
        val items = listOf(DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.FALLA, "DTC", "1 códigos: P0122", "Ábrelos"))
        val v2 = HealthReportFormato.escribir(items, MetricasChequeo(ltft = 2.3, stft = -6.2, dtcs = listOf("P0122")))

        val respuesta = JSONObject(tool(v2).call(JSONObject()))

        assertEquals(2, respuesta.getInt("formato"))
        assertEquals(4, respuesta.getInt("id"))
        assertEquals(-3.9, respuesta.getJSONObject("metricas").getDouble("correccionTotal"), 1e-9)
        assertEquals("P0122", respuesta.getJSONObject("metricas").getJSONArray("dtcs").getString(0))
    }

    @Test
    fun `sin chequeos lo dice`() = runTest {
        assertEquals("sin chequeos registrados", JSONObject(tool(null).call(JSONObject())).getString("error"))
    }
}
