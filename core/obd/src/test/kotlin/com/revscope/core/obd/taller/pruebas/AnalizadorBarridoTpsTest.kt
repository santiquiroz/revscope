package com.revscope.core.obd.taller.pruebas

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.BarridoSintetico.byteDeVoltios
import com.revscope.core.obd.taller.pruebas.BarridoSintetico.datos
import com.revscope.core.obd.taller.pruebas.BarridoSintetico.subidaYBajada
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.taller.sesion.Veredicto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalizadorBarridoTpsTest {

    private val tipicas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList())

    // Caso Benelli TNT 150i (25-sep-2026): 2,35 % cerrado, 9,0-9,4 % medio, 17,6-18,0 % a fondo y 2,75 % al final.
    private val benelli = datos(cerrado1 = listOf(6), medio = listOf(23, 24), fondo = listOf(45, 46), cerrado2 = listOf(7))

    // TPS sano sintético: ≈0,6 V cerrado, ≈2,5 V a la mitad y ≈4,4 V a fondo, con un escalón de ruido.
    private val sanoCerrado = byteDeVoltios(0.6)
    private val sanoFondo = byteDeVoltios(4.4)
    private val sano = datosConBarrido(subidaYBajada(sanoCerrado, sanoFondo))

    @Test
    fun `los datos del caso Benelli dan senal baja en todo el recorrido compatible con P0122`() {
        val resultado = AnalizadorBarridoTps.analizar(benelli, tipicas)
        val a = resultado.detalle as AnalisisBarridoTps

        assertEquals(PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO, a.patron)
        assertEquals(Veredicto.FALLA, resultado.veredicto)
        assertEquals(0.12, v2(a.cerradoV), 0.0)
        assertEquals(0.89, v2(a.fondoV), 0.0)
        assertEquals(0.21, a.fraccionRecorrido!!, 0.005)
        assertEquals(3.65, a.recorridoTipicoV!!, 1e-9)
        assertEquals(0.02, v2(a.repetibilidadV), 0.0)
        assertEquals(0.44, a.linealidad!!, 0.005)
        assertTrue(a.ordenCorrecto)
        assertTrue(a.irregularidades.isEmpty())
        assertFalse(resultado.bajaConfianza)
        assertEquals(10.0, a.tasaHz, 0.2)
        assertEquals("Señal baja en todo el recorrido, pareja y estable", resultado.titulo)
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("compatible con P0122"))
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("0,12 V"))
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("21 %"))
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("Típico (editable)"))
        assertTrue(resultado.siguientePaso!!.contains("multímetro"))
    }

    @Test
    fun `por paso descarta el primer segundo y da n, extremos y pico a pico en porcentaje y voltios`() {
        val medio = analisis(benelli).paso(AnalizadorBarridoTps.Pasos.MEDIO)!!

        assertEquals(40, medio.n)
        assertEquals(9.02, FormatoTaller.redondear(medio.minPct, 2), 0.0)
        assertEquals(9.41, FormatoTaller.redondear(medio.maxPct, 2), 0.0)
        assertEquals(0.45, v2(medio.minV), 0.0)
        assertEquals(0.47, v2(medio.maxV), 0.0)
        assertEquals(0.02, v2(medio.ppV), 0.0)
        assertEquals(10.0, medio.hz, 0.3)
    }

    @Test
    fun `un TPS sano de 0,6 a 4,4 V es normal`() {
        val resultado = AnalizadorBarridoTps.analizar(sano, tipicas)
        val a = resultado.detalle as AnalisisBarridoTps

        assertEquals(PatronTps.NORMAL, a.patron)
        assertEquals(Veredicto.OK, resultado.veredicto)
        assertTrue(a.comprobaciones.filterNot { it.cumple }.toString(), a.comprobaciones.all { it.cumple })
        assertEquals(0.5, a.linealidad!!, 0.02)
        assertTrue(resultado.hallazgos.toString(), resultado.hallazgos.isEmpty())
    }

    @Test
    fun `un corte a 0 V a mitad del barrido da cortes o saltos`() {
        val barrido = subidaYBajada(sanoCerrado, sanoFondo).toMutableList().apply {
            this[20] = 0
            this[21] = 0
        }
        val resultado = AnalizadorBarridoTps.analizar(datosConBarrido(barrido), tipicas)
        val a = resultado.detalle as AnalisisBarridoTps

        assertEquals(PatronTps.CORTES_O_SALTOS, a.patron)
        assertEquals(Veredicto.FALLA, resultado.veredicto)
        assertEquals(1, a.irregularidades.count { it.tipo == TipoIrregularidad.CORTE })
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("P0124"))
    }

    @Test
    fun `cerrado en 1,4 V da senal alta compatible con P0123`() {
        val alto = byteDeVoltios(1.4)
        val resultado = AnalizadorBarridoTps.analizar(
            datos(listOf(alto), listOf(byteDeVoltios(2.9)), listOf(sanoFondo), listOf(alto)),
            tipicas,
        )

        assertEquals(PatronTps.SENAL_ALTA, (resultado.detalle as AnalisisBarridoTps).patron)
        assertEquals(Veredicto.FALLA, resultado.veredicto)
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("P0123"))
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("1,39 V"))
    }

    @Test
    fun `a 6 Hz las detecciones quedan con baja confianza por tasa`() {
        val lento = datos(listOf(sanoCerrado), listOf(byteDeVoltios(2.5)), listOf(sanoFondo), listOf(sanoCerrado), hz = 6.0)
        val resultado = AnalizadorBarridoTps.analizar(lento, tipicas)
        val a = resultado.detalle as AnalisisBarridoTps

        assertEquals(6.0, a.tasaHz, 0.2)
        assertTrue(resultado.bajaConfianza)
        assertTrue(resultado.hallazgos.toString(), resultado.hallazgos.any { it.contains("baja confianza") })
        assertEquals(PatronTps.NORMAL, a.patron)
    }

    @Test
    fun `una meseta en plena subida es zona muerta y el patron pasa a rango o desempeno`() {
        val subida = (0..40).map { sanoCerrado + (sanoFondo - sanoCerrado) * it / 40 }
        val conMeseta = subida.take(16) + List(6) { subida[16] } + subida.drop(17)
        val resultado = AnalizadorBarridoTps.analizar(datosConBarrido(conMeseta + subida.reversed().drop(1)), tipicas)
        val a = resultado.detalle as AnalisisBarridoTps

        assertEquals(1, a.irregularidades.count { it.tipo == TipoIrregularidad.ZONA_MUERTA })
        assertEquals(PatronTps.RANGO_DESEMPENO, a.patron)
        assertEquals(Veredicto.ATENCION, resultado.veredicto)
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("P0121"))
    }

    @Test
    fun `la interpretacion cita la banda editada por el usuario`() {
        val fondoUsuario = BandaReferencia(ClavesBanda.TPS_FONDO_V, 3.5, 4.2, "V", OrigenBanda.USUARIO)
        val resultado = AnalizadorBarridoTps.analizar(benelli, tipicas + (ClavesBanda.TPS_FONDO_V to fondoUsuario))

        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("3,5–4,2 V, Editado por ti"))
    }

    @Test
    fun `sin muestras en un sostenido no hay veredicto de patron y pide repetir`() {
        val sinMedio = benelli.copy(muestras = benelli.muestras.filterNot { it.tMicros / 1_000 in 6_000L..11_000L })
        val resultado = AnalizadorBarridoTps.analizar(sinMedio, tipicas)

        assertEquals(Veredicto.ATENCION, resultado.veredicto)
        assertEquals("Sin datos suficientes del TPS", resultado.titulo)
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("Medio"))
    }

    @Test
    fun `con una referencia de 4,8 V los voltios salen de esa referencia y se dice cual se uso`() {
        val a = analisis(benelli.copy(vref = ReferenciaVoltaje.editada(4.8)))

        assertEquals(0.11, v2(a.cerradoV), 0.0)
        assertEquals("Editado por ti", a.vref.origen)
        assertEquals(4.8, a.json().getJSONObject("vref").getDouble("voltios"), 0.0)
    }

    private fun analisis(d: DatosPrueba, bandas: Map<String, BandaReferencia> = tipicas): AnalisisBarridoTps =
        AnalizadorBarridoTps.analizar(d, bandas).detalle as AnalisisBarridoTps

    private fun v2(x: Double) = FormatoTaller.redondear(x, 2)

    private fun datosConBarrido(barrido: List<Int>) = datos(
        cerrado1 = listOf(sanoCerrado, sanoCerrado + 1),
        medio = listOf(byteDeVoltios(2.5)),
        fondo = listOf(sanoFondo, sanoFondo - 1),
        cerrado2 = listOf(sanoCerrado),
        barrido = barrido,
    )

    @Test
    fun `sin muestras del barrido lento no se declara normal ni sin zonas muertas`() {
        val resultado = AnalizadorBarridoTps.analizar(sinBarridoLento(sano), tipicas)
        val a = resultado.detalle as AnalisisBarridoTps

        assertEquals(PatronTps.SIN_BARRIDO, a.patron)
        assertEquals(Veredicto.ATENCION, resultado.veredicto)
        assertFalse(a.barridoEvaluado)
        assertNull(a.comprobacion(AnalizadorBarridoTps.CLAVE_SIN_ZONAS_MUERTAS))
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("no se buscaron saltos ni zonas muertas"))
        assertTrue(resultado.siguientePaso!!, resultado.siguientePaso!!.contains("barre el acelerador despacio"))
    }

    @Test
    fun `sin barrido lento la senal baja se mantiene y avisa que falto el barrido`() {
        val resultado = AnalizadorBarridoTps.analizar(sinBarridoLento(benelli), tipicas)

        assertEquals(PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO, (resultado.detalle as AnalisisBarridoTps).patron)
        assertTrue(
            resultado.hallazgos.toString(),
            resultado.hallazgos.any { it.startsWith("No llegaron muestras suficientes del barrido lento") },
        )
    }

    private fun sinBarridoLento(datos: DatosPrueba) =
        datos.copy(segmentos = datos.segmentos.filterNot { it.clave == AnalizadorBarridoTps.Pasos.BARRIDO_LENTO })
}
