package com.revscope.core.obd.alerts

import android.speech.tts.TextToSpeech
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
        assertEquals("Zero to 100 in 12.4 seconds", launchPhrase(english, null, 12_400))
        assertEquals("Cero a cien en 12,4 segundos", launchPhrase(spanish, null, 12_400))
        assertEquals("Zero to 100 in 1.0 seconds", launchPhrase(english, null, 1_000))
        assertEquals("Cero a cien en 1,0 segundos", launchPhrase(spanish, null, 1_000))
        assertEquals("Cero a sesenta en 8,2 segundos", launchPhrase(spanish, 8_200, null))
        assertEquals(null, launchPhrase(english, null, null))
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
