package com.revscope.core.obd.service

import com.revscope.core.common.i18n.Region
import com.revscope.core.common.i18n.formatRegionalMoney
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.obd.R
import com.revscope.core.uitesting.XmlStringResolver
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSummaryNotifierTest {
    private val resolver = XmlStringResolver.es(R::class.java)
    private val units = UnitFormatter(UnitSystem.METRIC, Locale("es"))
    private val nbsp = " "
    private val colombia = Locale("es", "CO")
    private val copEnColombia: (Double) -> String =
        { formatRegionalMoney(it, Region.CO, colombia, resolver.locale) }
    private val cuatroMilDoscientosCop = NumberFormat.getCurrencyInstance(colombia).apply {
        currency = Currency.getInstance("COP")
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }.format(4_200.0)

    private fun session(
        distanceKm: Float,
        maxSpeed: Int,
        durationMin: Long,
        fuelCostCop: Double? = null,
        ecoScore: Int? = null,
    ) = SessionEntity(
        id = 1L, vehicleProfileId = 0L,
        startedAt = 1_000_000L, endedAt = 1_000_000L + durationMin * 60_000,
        adapterName = "vLinker", maxRpm = 9000, maxSpeed = maxSpeed, distanceKm = distanceKm,
        fuelCostCop = fuelCostCop, ecoScore = ecoScore,
    )

    @Test
    fun `resumen con distancia velocidad y duracion`() {
        val text = TripSummaryNotifier.summaryText(session(23.4f, 82, 34), resolver, units, copEnColombia)
        assertEquals("23,4${nbsp}km · 82${nbsp}km/h máx · 34 min", text)
    }

    @Test
    fun `resumen agrega costo de combustible en COP cuando esta disponible`() {
        val text = TripSummaryNotifier.summaryText(
            session(23.4f, 82, 34, fuelCostCop = 4_200.0), resolver, units, copEnColombia,
        )
        assertEquals("23,4${nbsp}km · 82${nbsp}km/h máx · 34 min · $cuatroMilDoscientosCop", text)
        assertTrue(cuatroMilDoscientosCop.contains("$"))
        assertTrue(cuatroMilDoscientosCop.contains("4.200"))
    }

    @Test
    fun `resumen agrega eco score cuando esta disponible`() {
        val text = TripSummaryNotifier.summaryText(
            session(23.4f, 82, 34, ecoScore = 85), resolver, units, copEnColombia,
        )
        assertEquals("23,4${nbsp}km · 82${nbsp}km/h máx · 34 min · Eco 85", text)
    }

    @Test
    fun `resumen agrega costo y eco score juntos en orden`() {
        val text = TripSummaryNotifier.summaryText(
            session(23.4f, 82, 34, fuelCostCop = 4_200.0, ecoScore = 85),
            resolver,
            units,
            copEnColombia,
        )
        assertEquals("23,4${nbsp}km · 82${nbsp}km/h máx · 34 min · $cuatroMilDoscientosCop · Eco 85", text)
    }

    @Test
    fun `summary shows miles and mph in US units`() {
        val english = XmlStringResolver.en(R::class.java)
        val us = UnitFormatter(UnitSystem.US, Locale.US)
        val text = TripSummaryNotifier.summaryText(session(23.4f, 82, 34), english, us) {
            formatRegionalMoney(it, Region.US, Locale.US, english.locale)
        }
        assertEquals("14.5${nbsp}mi · 51${nbsp}mph max · 34 min", text)
    }

    @Test
    fun `US trip without a fuel price shows no cost`() {
        val english = XmlStringResolver.en(R::class.java)
        val us = UnitFormatter(UnitSystem.US, Locale.US)
        val text = TripSummaryNotifier.summaryText(session(23.4f, 82, 34, ecoScore = 70), english, us) {
            formatRegionalMoney(it, Region.US, Locale.US, english.locale)
        }
        assertEquals("14.5${nbsp}mi · 51${nbsp}mph max · 34 min · Eco 70", text)
        assertFalse(text.contains("$"))
    }

    @Test
    fun `US trip with a cost shows dollars`() {
        val english = XmlStringResolver.en(R::class.java)
        val us = UnitFormatter(UnitSystem.US, Locale.US)
        val text = TripSummaryNotifier.summaryText(session(23.4f, 82, 34, fuelCostCop = 4.2), english, us) {
            formatRegionalMoney(it, Region.US, Locale.US, english.locale)
        }
        assertTrue(text.endsWith(" · $4.20"))
    }

    @Test
    fun `viaje real se notifica`() {
        assertTrue(TripSummaryNotifier.shouldNotify(session(5.2f, 60, 12)))
    }

    @Test
    fun `prueba de garaje no se notifica`() {
        assertFalse(TripSummaryNotifier.shouldNotify(session(0.05f, 0, 3)))
    }
}
