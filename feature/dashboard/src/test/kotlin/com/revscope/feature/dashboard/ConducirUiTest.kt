package com.revscope.feature.dashboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.taller.pid.DisponibilidadPid
import com.revscope.feature.dashboard.gauges.ladoAcotado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConducirUiTest {

    @Test
    fun `el gauge se achica a la columna que le toca`() {
        assertEquals(110.dp, ladoAcotado(preferido = 160.dp, disponible = 110.dp))
        assertEquals(160.dp, ladoAcotado(preferido = 160.dp, disponible = 300.dp))
        assertEquals(220.dp, ladoAcotado(preferido = 220.dp, disponible = Dp.Infinity))
    }

    @Test
    fun `cada estado de conexion tiene su descripcion para el lector de pantalla`() {
        assertEquals(EnlaceAdaptador.CONECTADO, enlaceAdaptador(ConnectionState.Connected("Android-Vlink")))
        assertEquals(EnlaceAdaptador.CONECTANDO, enlaceAdaptador(ConnectionState.Connecting))
        assertEquals(EnlaceAdaptador.SIN_ADAPTADOR, enlaceAdaptador(ConnectionState.Disconnected))
        assertEquals("Sin adaptador conectado", EnlaceAdaptador.SIN_ADAPTADOR.descripcion)
    }

    @Test
    fun `en modo GPS los gauges se atenuan y no se repite el aviso de adaptador`() {
        val hero = EstadoConducirUi(modoGpsHero = true, viajeGpsActivo = true)
        val viajeGps = EstadoConducirUi(viajeGpsActivo = true)

        assertTrue(hero.gaugesAtenuados)
        assertFalse(hero.avisarSinAdaptador)
        assertTrue(viajeGps.avisarSinAdaptador)
        assertFalse(EstadoConducirUi(conectado = true).gaugesAtenuados)
    }

    @Test
    fun `el aviso de arrancada muestra 0-100 con el 0-60 entre parentesis`() {
        assertEquals("🏁 0-100 en 8.40s  (0-60: 4.10s)", textoArrancada(to60Ms = 4_100, to100Ms = 8_400)?.replace(',', '.'))
        assertEquals("🏁 0-60 en 4.10s", textoArrancada(to60Ms = 4_100, to100Ms = null)?.replace(',', '.'))
        assertNull(textoArrancada(to60Ms = null, to100Ms = null))
    }

    @Test
    fun `al desconectarse descarta lecturas anteriores y explica la ausencia`() {
        val lecturas = lecturasConMotivos(
            conectado = false,
            velocidadGps = false,
            rpm = 1_500f,
            velocidad = 45f,
            temperatura = 82f,
            boost = 9f,
            marcha = 3,
            disponibilidad = { pid, nombre ->
                DisponibilidadPid.resolver(pid, nombre, EstadoSoporte.Soportado)
            },
        )

        assertNull(lecturas.rpm)
        assertNull(lecturas.velocidad)
        assertNull(lecturas.temperatura)
        assertNull(lecturas.boost)
        assertNull(lecturas.marcha)
        assertEquals("Sin adaptador", lecturas.motivoRpm)
        assertEquals("Sin adaptador", lecturas.motivoTemperatura)
    }
}
