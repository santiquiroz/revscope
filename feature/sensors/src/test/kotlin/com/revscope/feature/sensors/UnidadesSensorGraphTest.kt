package com.revscope.feature.sensors

import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.obd.R as ObdR
import com.revscope.core.obd.taller.grafica.EstadoVref
import com.revscope.core.obd.taller.grafica.UnidadPosicion
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnidadesSensorGraphTest {

    private val english = XmlStringResolver.en(
        R::class.java,
        ObdR::class.java,
        resDirs = listOf("src/main/res", "../../core/obd/src/main/res"),
    )

    private val unidades = mapOf("31" to "km", "0D" to "km/h", "0C" to "rpm")

    private fun entrada() = EntradaGraficaCaptura(
        series = mapOf(
            "31" to listOf(0L to 1234.5),
            "0D" to listOf(0L to 100.0),
            "0C" to listOf(0L to 3000.0),
        ),
        nombre = { it },
        unidadPid = { unidades[it].orEmpty() },
        hz = emptyMap(),
        unidad = UnidadPosicion.PORCENTAJE,
        referencia = EstadoVref(ReferenciaVoltaje.TIPICA, null, null),
        ventanaMs = 10_000,
        pausada = false,
        bandas = emptyMap(),
    )

    private fun grafica(system: UnitSystem) =
        MapeoGraficaCaptura.de(entrada().enUnidades(UnitFormatter(system, Locale.US)), english)

    private fun GraficaCapturaUi.valor(pid: String) = valores.first { it.pid == pid }.valor

    private fun GraficaCapturaUi.ultimoY(pid: String) =
        paneles.flatMap { it.modelo.series }.first { it.etiqueta == pid }.puntos.last().y

    @Test
    fun `en US la distancia y la velocidad llegan convertidas a la grafica`() {
        val ui = grafica(UnitSystem.US)

        assertEquals(767.08, ui.ultimoY("31"), 0.01)
        assertEquals("767 mi", ui.valor("31"))
        assertEquals(62.14, ui.ultimoY("0D"), 0.01)
        assertEquals("62.1 mph", ui.valor("0D"))
        assertEquals("3000 rpm", ui.valor("0C"))
    }

    @Test
    fun `en US el titulo del panel nombra la unidad mostrada`() {
        val titulos = grafica(UnitSystem.US).paneles.map { it.titulo }

        assertEquals(true, titulos.any { "mi" in it && "km" !in it })
        assertEquals(true, titulos.any { "mph" in it })
    }

    @Test
    fun `en metrico la grafica queda igual que sin convertir`() {
        val metrico = grafica(UnitSystem.METRIC)

        assertEquals(MapeoGraficaCaptura.de(entrada(), english), metrico)
        assertEquals(1234.5, metrico.ultimoY("31"), 0.0)
        assertEquals("100 km/h", metrico.valor("0D"))
    }
}
