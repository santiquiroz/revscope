package com.revscope.feature.session

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCsvLocaleTest {

    @Test
    fun `decimal CSV column always uses dot with Spanish default locale`() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("es"))

            assertEquals("gps,12.5", csvLine("gps", 12.5))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
