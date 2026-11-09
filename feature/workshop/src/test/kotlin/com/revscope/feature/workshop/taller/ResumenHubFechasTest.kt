package com.revscope.feature.workshop.taller

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.taller.sesion.SesionTaller
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumenHubFechasTest {

    private val zona: ZoneId = ZoneId.of("America/Bogota")
    private val inicio = 1_783_810_800_000L
    private val cierre = inicio + 50 * 60_000L

    private fun estado(locale: Locale) = ResumenHub.de(
        DatosHub(
            BENELLI,
            listOf(SesionTaller(id = 9, vehiculoId = BENELLI.id, inicio = inicio, cierre = cierre, titulo = "Routine check")),
            emptyList(),
        ),
        ConnectionState.Disconnected,
        verTodas = false,
        zona = zona,
        locale = locale,
    )

    @Test
    fun `en inglés la sesión anterior usa el formato Jul 11, 2026`() {
        assertEquals("Jul 11, 2026, 18:00 – 18:50", estado(Locale.US).anteriores.single().fecha)
    }

    @Test
    fun `en español la sesión anterior usa FechasTaller`() {
        assertEquals(
            FechasTaller.fechaHora(inicio, zona) + " – " + FechasTaller.hora(cierre, zona),
            estado(Locale("es")).anteriores.single().fecha,
        )
    }
}
