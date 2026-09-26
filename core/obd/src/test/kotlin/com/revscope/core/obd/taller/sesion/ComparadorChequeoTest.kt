package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.BandasTipicas
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.workshop.MetricasChequeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparadorChequeoTest {

    private val umbralesTipicos = UmbralesComparacion.desde(BandasTipicas.para(VehicleType.MOTORCYCLE))

    // Chequeo del 11-jul de la Benelli TNT 150i: sin códigos, monitores completos, LTFT +2,3 %, corrección −3,9 %.
    private val base11Julio = MetricasChequeo(
        stft = -6.2,
        ltft = 2.3,
        voltaje = 13.7,
        motorEncendido = true,
        ect = 79.0,
        monitoresCompletos = 3,
        monitoresTotales = 3,
        dtcs = emptyList(),
    )

    private val ahoraConP0122 = MetricasChequeo(
        stft = -4.0,
        ltft = 2.3,
        voltaje = 14.2,
        motorEncendido = true,
        ect = 78.0,
        monitoresCompletos = 3,
        monitoresTotales = 3,
        dtcs = listOf("P0122"),
    )

    private fun comparar(base: MetricasChequeo = base11Julio, ahora: MetricasChequeo = ahoraConP0122) =
        ComparadorChequeo.comparar(base, ahora, umbralesTipicos).associateBy { it.metrica }

    @Test
    fun `un código que no estaba en la base es una FALLA y lo nombra`() {
        val codigos = comparar().getValue(MetricaComparada.CODIGOS)

        assertEquals(Veredicto.FALLA, codigos.veredicto)
        assertEquals("Ninguno", codigos.base)
        assertEquals("P0122", codigos.ahora)
        assertTrue(codigos.cambio, codigos.cambio.contains("P0122"))
    }

    @Test
    fun `el voltaje de 13,7 V a 14,2 V con el motor encendido no es un cambio relevante`() {
        val voltaje = comparar().getValue(MetricaComparada.VOLTAJE)

        assertEquals(Veredicto.OK, voltaje.veredicto)
        assertEquals("13,7 V", voltaje.base)
        assertEquals("14,2 V", voltaje.ahora)
        assertTrue(voltaje.cambio, voltaje.cambio.startsWith("Sin cambio relevante"))
        assertEquals("±0,5 V · Típico (editable)", voltaje.referencia)
    }

    @Test
    fun `los ajustes muestran la base del caso con signo y coma decimal`() {
        val filas = comparar()

        assertEquals("+2,3 %", filas.getValue(MetricaComparada.AJUSTE_LARGO).base)
        assertEquals("-3,9 %", filas.getValue(MetricaComparada.CORRECCION_TOTAL).base)
        assertEquals("-1,7 %", filas.getValue(MetricaComparada.CORRECCION_TOTAL).ahora)
        assertEquals(Veredicto.OK, filas.getValue(MetricaComparada.CORRECCION_TOTAL).veredicto)
    }

    @Test
    fun `un ajuste que se mueve más que el umbral pide atención pero no se declara falla`() {
        val ahora = ahoraConP0122.copy(ltft = 9.0)

        val ajuste = comparar(ahora = ahora).getValue(MetricaComparada.AJUSTE_LARGO)

        assertEquals(Veredicto.ATENCION, ajuste.veredicto)
        assertEquals("Cambió +6,7 pp", ajuste.cambio)
    }

    @Test
    fun `el umbral editado por el usuario manda y se dice que es suyo`() {
        val usuario = BandaReferencia(ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V, null, 0.3, "V", OrigenBanda.USUARIO)
        val umbrales = UmbralesComparacion.desde(
            BandasTipicas.para(VehicleType.MOTORCYCLE) + (ClavesBanda.CHEQUEO_VOLTAJE_DELTA_V to usuario),
        )

        val voltaje = ComparadorChequeo.comparar(base11Julio, ahoraConP0122, umbrales)
            .first { it.metrica == MetricaComparada.VOLTAJE }

        assertEquals(Veredicto.ATENCION, voltaje.veredicto)
        assertEquals("±0,3 V · Editado por ti", voltaje.referencia)
    }

    @Test
    fun `el voltaje con motor apagado contra encendido no se compara`() {
        val ahora = ahoraConP0122.copy(voltaje = 12.2, motorEncendido = false)

        val voltaje = comparar(ahora = ahora).getValue(MetricaComparada.VOLTAJE)

        assertEquals(Veredicto.INFO, voltaje.veredicto)
        assertTrue(voltaje.cambio, voltaje.cambio.startsWith("No comparable"))
    }

    @Test
    fun `la temperatura del motor es contexto y avisa si ahora está frío`() {
        val motor = comparar(ahora = ahoraConP0122.copy(ect = 32.0)).getValue(MetricaComparada.MOTOR)

        assertEquals(Veredicto.INFO, motor.veredicto)
        assertEquals("79 °C", motor.base)
        assertEquals("32 °C", motor.ahora)
        assertTrue(motor.cambio, motor.cambio.contains("frío"))
    }

    @Test
    fun `los códigos que desaparecen son buena noticia y los que siguen piden atención`() {
        val conCodigo = base11Julio.copy(dtcs = listOf("P0122"))

        val desaparecio = comparar(base = conCodigo, ahora = ahoraConP0122.copy(dtcs = emptyList()))
        val persiste = comparar(base = conCodigo)

        assertEquals(Veredicto.OK, desaparecio.getValue(MetricaComparada.CODIGOS).veredicto)
        assertEquals(Veredicto.ATENCION, persiste.getValue(MetricaComparada.CODIGOS).veredicto)
    }

    @Test
    fun `sin lectura de códigos en un lado no se inventa una comparación`() {
        val codigos = comparar(ahora = ahoraConP0122.copy(dtcs = emptyList(), dtcsLeidos = false))
            .getValue(MetricaComparada.CODIGOS)

        assertEquals(Veredicto.INFO, codigos.veredicto)
        assertEquals("Sin dato", codigos.ahora)
    }

    @Test
    fun `menos monitores completos que en la base pide atención`() {
        val monitores = comparar(ahora = ahoraConP0122.copy(monitoresCompletos = 1))
            .getValue(MetricaComparada.MONITORES)

        assertEquals(Veredicto.ATENCION, monitores.veredicto)
        assertEquals("3 de 3", monitores.base)
        assertEquals("1 de 3", monitores.ahora)
    }

    @Test
    fun `una métrica sin dato en ningún lado no aparece y con dato en uno solo se muestra sin veredicto`() {
        val soloBase = comparar(ahora = ahoraConP0122.copy(stft = null, ltft = null))

        assertEquals(Veredicto.INFO, soloBase.getValue(MetricaComparada.AJUSTE_LARGO).veredicto)
        assertEquals("—", soloBase.getValue(MetricaComparada.AJUSTE_LARGO).ahora)
        val ninguno = comparar(base = base11Julio.copy(ect = null), ahora = ahoraConP0122.copy(ect = null))
        assertFalse(MetricaComparada.MOTOR in ninguno)
        assertNull(ninguno[MetricaComparada.MOTOR])
    }

    @Test
    fun `las filas salen en el orden de la tabla del informe`() {
        assertEquals(
            listOf(
                MetricaComparada.CODIGOS, MetricaComparada.AJUSTE_LARGO, MetricaComparada.CORRECCION_TOTAL,
                MetricaComparada.VOLTAJE, MetricaComparada.MOTOR, MetricaComparada.MONITORES,
            ),
            ComparadorChequeo.comparar(base11Julio, ahoraConP0122, umbralesTipicos).map { it.metrica },
        )
    }
}
