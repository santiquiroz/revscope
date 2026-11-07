package com.revscope.core.obd.service

import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.obd.R
import com.revscope.core.uitesting.XmlStringResolver
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveReadingsTextTest {
    private val english = XmlStringResolver.en(R::class.java)
    private val spanish = XmlStringResolver.es(R::class.java)
    private val nbsp = " "

    private fun text(system: UnitSystem, resolver: XmlStringResolver = english) =
        liveReadingsText(80.0, 90.0, 12.6, resolver, UnitFormatter(system, resolver.locale))

    @Test
    fun `metric keeps kilometers per hour and celsius`() {
        assertEquals("80${nbsp}km/h  ·  90${nbsp}°C  ·  12,6V", text(UnitSystem.METRIC, spanish))
    }

    @Test
    fun `us shows mph and fahrenheit`() {
        assertEquals("50${nbsp}mph  ·  194${nbsp}°F  ·  12.6V", text(UnitSystem.US))
    }

    @Test
    fun `uk shows mph and keeps celsius`() {
        assertEquals("50${nbsp}mph  ·  90${nbsp}°C  ·  12.6V", text(UnitSystem.UK))
    }

    @Test
    fun `no readings gives empty text`() {
        assertEquals("", liveReadingsText(null, null, null, english, UnitFormatter(UnitSystem.US, Locale.US)))
    }
}
