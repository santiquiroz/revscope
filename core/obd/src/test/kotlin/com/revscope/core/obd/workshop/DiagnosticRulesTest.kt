package com.revscope.core.obd.workshop

import com.revscope.core.obd.R
import com.revscope.core.obd.protocol.ReadinessParser
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRulesTest {

    private val es = XmlStringResolver.es(R::class.java)

    @Test
    fun `total trim warning is localized in English`() {
        val diagnosis = DiagnosticRules.evaluarTrimCombinado(
            stft = 10.0,
            ltft = 8.0,
            resolver = XmlStringResolver.en(R::class.java),
        )

        assertEquals(
            "Short- plus long-term trim exceeds ±15.0% — active mixture condition",
            diagnosis.causaProbable,
        )
    }

    @Test
    fun `ltft dentro de rango es ok`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarFuelTrimLargo(5.0, es).nivel)
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarFuelTrimLargo(-9.9, es).nivel)
    }

    @Test
    fun `ltft positivo alto es mezcla pobre`() {
        val d = DiagnosticRules.evaluarFuelTrimLargo(18.0, es)
        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
        assertTrue(d.causaProbable.contains("pobre", ignoreCase = true))
    }

    @Test
    fun `ltft negativo alto es mezcla rica`() {
        val d = DiagnosticRules.evaluarFuelTrimLargo(-15.0, es)
        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
        assertTrue(d.causaProbable.contains("rica", ignoreCase = true))
    }

    @Test
    fun `ltft extremo es falla`() {
        assertEquals(DiagnosticRules.Nivel.FALLA, DiagnosticRules.evaluarFuelTrimLargo(30.0, es).nivel)
        assertEquals(DiagnosticRules.Nivel.FALLA, DiagnosticRules.evaluarFuelTrimLargo(-26.0, es).nivel)
    }

    @Test
    fun `correccion combinada excesiva`() {
        val d = DiagnosticRules.evaluarTrimCombinado(stft = 10.0, ltft = 8.0, resolver = es)
        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
    }

    @Test
    fun `o2 clavado bajo es sensor perezoso o mezcla extrema`() {
        val muestras = List(35) { 0.1 }
        assertEquals(DiagnosticRules.Nivel.ATENCION, DiagnosticRules.evaluarO2(muestras, es).nivel)
    }

    @Test
    fun `o2 oscilando es ok`() {
        val muestras = List(40) { if (it % 2 == 0) 0.2 else 0.8 }
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarO2(muestras, es).nivel)
    }

    @Test
    fun `o2 sin muestras suficientes no diagnostica`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarO2(List(5) { 0.1 }, es).nivel)
    }

    @Test
    fun `voltaje bajo en marcha apunta al alternador`() {
        val d = DiagnosticRules.evaluarVoltaje(12.8, motorEncendido = true, resolver = es)
        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
        assertTrue(d.causaProbable.contains("alternador", ignoreCase = true))
    }

    @Test
    fun `voltaje sano en marcha es ok`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarVoltaje(14.2, true, es).nivel)
    }

    @Test
    fun `sobrecalentamiento es falla`() {
        assertEquals(DiagnosticRules.Nivel.FALLA, DiagnosticRules.evaluarTemperatura(108.0, es).nivel)
    }

    @Test
    fun `temperatura normal ok`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarTemperatura(88.0, es).nivel)
    }

    @Test
    fun `readiness con mil encendida es falla`() {
        val status = ReadinessParser.ReadinessStatus(
            milOn = true, dtcCount = 2, isDiesel = false,
            monitors = emptyList(),
        )
        val ds = DiagnosticRules.evaluarReadiness(status, es)
        assertTrue(ds.any { it.nivel == DiagnosticRules.Nivel.FALLA })
    }

    @Test
    fun `monitor incompleto avisa no listo para tecnomecanica`() {
        val status = ReadinessParser.ReadinessStatus(
            milOn = false, dtcCount = 0, isDiesel = false,
            monitors = listOf(ReadinessParser.MonitorResult("Catalizador", true, false)),
        )
        val ds = DiagnosticRules.evaluarReadiness(status, es)
        assertTrue(ds.any { it.nivel == DiagnosticRules.Nivel.ATENCION && it.titulo.contains("Catalizador") })
    }

    @Test
    fun `readiness completo es ok`() {
        val status = ReadinessParser.ReadinessStatus(
            milOn = false, dtcCount = 0, isDiesel = false,
            monitors = listOf(ReadinessParser.MonitorResult("Catalizador", true, true)),
        )
        val ds = DiagnosticRules.evaluarReadiness(status, es)
        assertTrue(ds.all { it.nivel == DiagnosticRules.Nivel.OK })
    }

    @Test
    fun `ltft exactamente en 25 es atencion no falla`() {
        assertEquals(DiagnosticRules.Nivel.ATENCION, DiagnosticRules.evaluarFuelTrimLargo(25.0, es).nivel)
        assertEquals(DiagnosticRules.Nivel.ATENCION, DiagnosticRules.evaluarFuelTrimLargo(-25.0, es).nivel)
    }

    @Test
    fun `ltft exactamente en 10 es ok`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarFuelTrimLargo(10.0, es).nivel)
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarFuelTrimLargo(-10.0, es).nivel)
    }

    @Test
    fun `bateria baja con motor apagado`() {
        val d = DiagnosticRules.evaluarVoltaje(11.5, motorEncendido = false, resolver = es)
        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
        assertTrue(d.causaProbable.contains("Batería", ignoreCase = true) || d.titulo.contains("Batería", ignoreCase = true))
    }

    @Test
    fun `correccion combinada dentro de rango es ok`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarTrimCombinado(5.0, 5.0, es).nivel)
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarTrimCombinado(-7.5, -7.5, es).nivel)
    }

    @Test
    fun `monitor no soportado se excluye del reporte`() {
        val status = ReadinessParser.ReadinessStatus(
            milOn = false, dtcCount = 0, isDiesel = false,
            monitors = listOf(ReadinessParser.MonitorResult("Sistema EVAP", false, false)),
        )
        val ds = DiagnosticRules.evaluarReadiness(status, es)
        assertTrue(ds.none { it.titulo.contains("EVAP") })
    }
}
