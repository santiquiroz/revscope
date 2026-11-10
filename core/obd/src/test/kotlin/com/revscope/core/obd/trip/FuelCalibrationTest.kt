package com.revscope.core.obd.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelCalibrationTest {

    @Test
    fun `tres intervalos limpios sugieren el factor agregado`() {
        val intervalos = (0..2).map { IntervaloTanqueo(it * 10L, it * 10L + 9, 10.0, 9.0, false) }
        val resultado = FuelCalibration.evaluar(intervalos, 1.0, 0)

        assertEquals(1.0 * 30.0 / 27.0, resultado.factorSugerido!!, 1e-9)
    }

    @Test
    fun `parcial entre llenos se suma al volumen real`() {
        val intervalos = FuelCalibration.intervalos(
            listOf(Tanqueo(1, 10.0, true), Tanqueo(2, 3.0, false), Tanqueo(3, 8.0, true)),
            emptyList(),
        )
        assertEquals(11.0, intervalos.single().litrosReales, 0.0)
    }

    @Test
    fun `viaje sin litros y distancia suficiente marca incompleto`() {
        val intervalo = intervaloConViajeSinLitros(0.5)
        assertTrue(intervalo.incompleto)
        assertEquals(MotivoIgnorado.INCOMPLETO, FuelCalibration.evaluar(listOf(intervalo), 1.0, 0).ignorados.single().second)
    }

    @Test
    fun `viaje sin litros con distancia cero no marca incompleto`() {
        val intervalo = intervaloConViajeSinLitros(0.0)
        assertTrue(!intervalo.incompleto)
        assertTrue(FuelCalibration.evaluar(listOf(intervalo), 1.0, 0).usados.contains(intervalo))
    }

    @Test
    fun `desvio superior al veinticinco por ciento se ignora`() {
        val resultado = FuelCalibration.evaluar(listOf(IntervaloTanqueo(1, 2, 10.0, 12.6, false)), 1.0, 0)
        assertEquals(MotivoIgnorado.DESVIO, resultado.ignorados.single().second)
    }

    @Test
    fun `menos de tres usados no sugiere y calcula faltantes`() {
        val resultado = FuelCalibration.evaluar(listOf(intervaloCon(), intervaloCon(3)), 1.0, 0)
        assertNull(resultado.factorSugerido)
        assertEquals(1, resultado.faltan)
    }

    @Test
    fun `intervalo anterior al cambio de factor se ignora primero`() {
        val resultado = FuelCalibration.evaluar(listOf(intervaloCon()), 1.0, 2)
        assertEquals(MotivoIgnorado.ANTES_DEL_FACTOR, resultado.ignorados.single().second)
    }

    @Test
    fun `ratio dentro del uno por ciento no sugiere`() {
        val intervalos = (0..2).map { IntervaloTanqueo(it * 10L, it * 10L + 9, 10.0, 9.95, false) }
        assertNull(FuelCalibration.evaluar(intervalos, 1.0, 0).factorSugerido)
    }

    @Test
    fun `factor sugerido se recorta y no propone cuando ya esta en el tope`() {
        val sobreestimado = (0..2).map { IntervaloTanqueo(it * 10L, it * 10L + 9, 10.0, 8.0, false) }
        val bajo = (0..2).map { IntervaloTanqueo(it * 10L, it * 10L + 9, 10.0, 12.5, false) }
        assertEquals(1.3, FuelCalibration.evaluar(sobreestimado, 1.1, 0).factorSugerido!!, 0.0)
        assertEquals(0.7, FuelCalibration.evaluar(bajo, 0.8, 0).factorSugerido!!, 0.0)
        assertNull(FuelCalibration.evaluar(sobreestimado, 1.3, 0).factorSugerido)
    }

    @Test
    fun `ordena tanqueos e ignora parciales antes del primer lleno`() {
        val intervalos = FuelCalibration.intervalos(
            listOf(Tanqueo(3, 5.0, true), Tanqueo(0, 8.0, false), Tanqueo(1, 10.0, true), Tanqueo(2, 2.0, false)),
            emptyList(),
        )
        assertEquals(1, intervalos.size)
        assertEquals(7.0, intervalos.single().litrosReales, 0.0)
    }

    @Test
    fun `limites del inicio de viaje son abiertos al inicio y cerrados al final`() {
        val intervalos = FuelCalibration.intervalos(
            listOf(Tanqueo(1, 10.0, true), Tanqueo(5, 10.0, true)),
            listOf(ViajeCombustible(1, 100.0, 1.0), ViajeCombustible(5, 10.0, 1.0)),
        )
        assertEquals(10.0, intervalos.single().litrosEstimados, 0.0)
    }

    @Test
    fun `parsea litros y galones y rechaza volumenes invalidos`() {
        assertEquals(3.5, FuelCalibration.parseVolumenTanqueo(" 3,5 ", false)!!, 0.0)
        assertEquals(3.5, FuelCalibration.parseVolumenTanqueo("3.5", false)!!, 0.0)
        assertEquals(3.78541, FuelCalibration.parseVolumenTanqueo("1", true)!!, 0.0)
        assertNull(FuelCalibration.parseVolumenTanqueo("0", false))
        assertNull(FuelCalibration.parseVolumenTanqueo("-1", false))
        assertNull(FuelCalibration.parseVolumenTanqueo("200.01", false))
        assertNull(FuelCalibration.parseVolumenTanqueo("n/a", false))
    }

    private fun intervaloCon(timestamp: Long = 1): IntervaloTanqueo =
        IntervaloTanqueo(timestamp, timestamp + 1, 10.0, 9.0, incompleto = false)

    private fun intervaloConViajeSinLitros(distanciaKm: Double): IntervaloTanqueo = FuelCalibration.intervalos(
        tanqueos = listOf(Tanqueo(1, 10.0, true), Tanqueo(3, 10.0, true)),
        viajes = listOf(ViajeCombustible(2, null, distanciaKm), ViajeCombustible(2, 9.0, 1.0)),
    ).single()
}
