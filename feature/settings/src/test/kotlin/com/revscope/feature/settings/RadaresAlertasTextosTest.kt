package com.revscope.feature.settings

import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class RadaresAlertasTextosTest {

    private val es = XmlStringResolver.es(R::class.java)
    private val en = XmlStringResolver.en(R::class.java)

    @Test
    fun `spanish ai pico y placa subtitle keeps the original wording`() {
        assertEquals(
            "Investiga la restricción de la ciudad donde estés (pico y placa, hoy no circula, rodízio…) " +
                "y la guarda hasta que venza",
            es.get(R.string.settings_alertas_ai_pico_placa_supported),
        )
    }

    @Test
    fun `english ai pico y placa subtitle reads naturally`() {
        assertEquals(
            "Looks up the restriction for the city you're in (pico y placa, hoy no circula, rodízio…) " +
                "and keeps it until it expires",
            en.get(R.string.settings_alertas_ai_pico_placa_supported),
        )
    }

    @Test
    fun `spanish camera description keeps the original wording`() {
        assertEquals(
            "Descarga los radares en 50 km a tu alrededor. " +
                "Fuentes: OpenStreetMap + registro oficial ANSV · se actualiza cada semana automáticamente. " +
                "Al conducir, la app avisa por voz al acercarte (funciona offline tras descargar).",
            es.get(R.string.settings_alertas_cameras_description),
        )
    }

    @Test
    fun `pico y placa example keeps the json intact in both languages`() {
        val json = "{\"cityId\":\"medellin\",\"displayName\":\"Medellín\"," +
            "\"rotation\":{\"2\":[1,7],\"3\":[0,3]}," +
            "\"startHour\":5,\"endHour\":20," +
            "\"carDigit\":\"LAST\",\"motoDigit\":\"FIRST\"," +
            "\"validFromMs\":1770008400000,\"validUntilMs\":1785560399000}"

        assertEquals(
            "Ejemplo (Medellín, rotación por día de semana):\n$json",
            es.get(R.string.settings_alertas_pico_placa_example),
        )
        assertEquals(
            "Example (Medellín, rotation by day of the week):\n$json",
            en.get(R.string.settings_alertas_pico_placa_example),
        )
    }
}
