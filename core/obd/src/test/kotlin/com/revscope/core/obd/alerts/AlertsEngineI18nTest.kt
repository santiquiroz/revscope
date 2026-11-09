package com.revscope.core.obd.alerts

import android.speech.tts.TextToSpeech
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.obd.R
import com.revscope.core.uitesting.XmlStringResolver
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertsEngineI18nTest {

    private val english = XmlStringResolver.en(R::class.java)
    private val spanish = XmlStringResolver.es(R::class.java)

    @Test
    fun `lap phrase uses locale decimal and minute plural`() {
        assertEquals("Lap 3: 1 minute 12.4", lapPhrase(english, 3, 72_400))
        assertEquals("Vuelta 3: 1 minuto 12,4", lapPhrase(spanish, 3, 72_400))
        assertEquals("Lap 3: 1.0 seconds", lapPhrase(english, 3, 1_000))
        assertEquals("Vuelta 3: 1,0 segundos", lapPhrase(spanish, 3, 1_000))
        assertEquals("Lap 3: 2 minutes 12.4", lapPhrase(english, 3, 132_400))
        assertEquals("Vuelta 3: 2 minutos 12,4", lapPhrase(spanish, 3, 132_400))
    }

    @Test
    fun `launch result uses locale decimal`() {
        assertEquals("Zero to 100 in 12.4 seconds", metricLaunch(english, null, 12_400))
        assertEquals("Cero a cien en 12,4 segundos", metricLaunch(spanish, null, 12_400))
        assertEquals("Zero to 100 in 1.0 seconds", metricLaunch(english, null, 1_000))
        assertEquals("Cero a cien en 1,0 segundos", metricLaunch(spanish, null, 1_000))
        assertEquals("Cero a sesenta en 8,2 segundos", metricLaunch(spanish, 8_200, null))
        assertEquals(null, metricLaunch(english, null, null))
    }

    @Test
    fun `imperial launch speaks only the zero to sixty mph time`() {
        assertEquals(
            "Zero to sixty miles per hour in 7.9 seconds",
            launchPhrase(english, 4_100, 8_400, 7_900, UnitSystem.US),
        )
        assertEquals(
            "De cero a sesenta millas por hora en 7,9 segundos",
            launchPhrase(spanish, 4_100, null, 7_900, UnitSystem.UK),
        )
        assertEquals(null, launchPhrase(english, 4_100, null, null, UnitSystem.US))
    }

    private fun metricLaunch(resolver: XmlStringResolver, to60Ms: Long?, to100Ms: Long?) =
        launchPhrase(resolver, to60Ms, to100Ms, 7_900, UnitSystem.METRIC)

    private val metric = UnitFormatter(UnitSystem.METRIC, Locale("es"))
    private val us = UnitFormatter(UnitSystem.US, Locale.US)
    private val uk = UnitFormatter(UnitSystem.UK, Locale.UK)

    @Test
    fun `overheat speaks degrees in the chosen temperature unit`() {
        assertEquals("Temperatura de motor 105 grados", overheatPhrase(spanish, metric, 105.0))
        assertEquals("Engine temperature 221 degrees", overheatPhrase(english, us, 105.0))
        assertEquals("Engine temperature 105 degrees", overheatPhrase(english, uk, 105.0))
    }

    @Test
    fun `speed camera speaks distance and limit as words`() {
        assertEquals(
            "Radar a 300 metros, límite 80 kilómetros por hora",
            speedCameraPhrase(spanish, metric, 320, 80),
        )
        assertEquals(
            "Speed camera in 0.2 miles, speed limit 50 miles per hour",
            speedCameraPhrase(english, us, 320, 80),
        )
        assertEquals("Speed camera in 330 feet", speedCameraPhrase(english, uk, 140, null))
    }

    @Test
    fun `pothole speaks meters or feet`() {
        assertEquals("Hueco reportado a 187 metros", potholePhrase(spanish, metric, 187))
        assertEquals("Reported pothole 390 feet ahead", potholePhrase(english, us, 120))
    }

    @Test
    fun `whole miles keep singular wording`() {
        assertEquals("1 mile", spokenDistance(english, us, 1_609))
        assertEquals("1 kilómetro", spokenDistance(spanish, metric, 1_000))
        assertEquals("1 mile per hour", spokenSpeed(english, us, 1.609344))
    }

    @Test
    fun `tts uses requested locale when available`() {
        assertEquals(
            Locale.US,
            chooseTtsLocale(Locale.US, TextToSpeech.LANG_AVAILABLE, Locale("es")),
        )
    }

    @Test
    fun `tts falls back to engine default when locale data is missing or unsupported`() {
        val engineDefault = Locale("es")
        assertEquals(
            engineDefault,
            chooseTtsLocale(Locale.US, TextToSpeech.LANG_MISSING_DATA, engineDefault),
        )
        assertEquals(
            engineDefault,
            chooseTtsLocale(Locale.US, TextToSpeech.LANG_NOT_SUPPORTED, engineDefault),
        )
    }
}
