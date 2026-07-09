package com.revscope.core.obd.trip

private const val MS_PER_HOUR = 3_600_000.0
private const val MS_PER_SECOND = 1_000.0
private const val AIR_FUEL_RATIO_STOICH = 14.7
private const val GASOLINE_DENSITY_G_PER_L = 750.0

/**
 * Pure fuel-cost math over recorded telemetry points — no I/O, fully unit-testable.
 * Mirrors [com.revscope.core.obd.telemetry.TripStatsCalculator]'s trapezoidal-integration style.
 */
object FuelCostCalculator {

    const val LITERS_PER_GALLON = 3.78541

    data class FuelResult(val liters: Double, val costCop: Double, val estimado: Boolean)

    /**
     * Trapezoidal integration of fuel rate (L/h, PID 5E) over time → liters consumed.
     * Direct sensor reading — not an estimate.
     */
    fun fromFuelRate(points: List<Pair<Long, Double>>, precioGalonCop: Double): FuelResult? {
        if (points.size < 2) return null
        val liters = integrateTrapezoidal(points) / MS_PER_HOUR
        return FuelResult(liters, costFromLiters(liters, precioGalonCop), estimado = false)
    }

    /**
     * Fallback when the ECU doesn't support PID 5E: derives fuel burn from mass airflow
     * (PID 10, g/s) via the stoichiometric air-fuel ratio and gasoline density.
     */
    fun fromMaf(points: List<Pair<Long, Double>>, precioGalonCop: Double): FuelResult? {
        if (points.size < 2) return null
        val fuelRatePoints = points.map { (timestampMs, maf) -> timestampMs to maf / AIR_FUEL_RATIO_STOICH }
        val fuelGrams = integrateTrapezoidal(fuelRatePoints) / MS_PER_SECOND
        val liters = fuelGrams / GASOLINE_DENSITY_G_PER_L
        return FuelResult(liters, costFromLiters(liters, precioGalonCop), estimado = true)
    }

    private fun costFromLiters(liters: Double, precioGalonCop: Double): Double =
        liters / LITERS_PER_GALLON * precioGalonCop

    /** Sum of trapezoid areas under consecutive (timestampMs, value) points, in value·ms. */
    private fun integrateTrapezoidal(points: List<Pair<Long, Double>>): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            val (prevMs, prevValue) = points[i - 1]
            val (currMs, currValue) = points[i]
            val dtMs = currMs - prevMs
            if (dtMs <= 0) continue
            total += (prevValue + currValue) / 2.0 * dtMs
        }
        return total
    }
}
