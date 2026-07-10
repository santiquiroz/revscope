package com.revscope.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/** Precios de combustible por tipo, en COP por galón. */
data class FuelPrices(
    val corriente: Double,
    val extra: Double,
    val diesel: Double,
)

/**
 * No existe en datos.gov.co (Socrata) un dataset de precios de gasolina/diésel por
 * municipio que esté vigente y mantenido — se investigó exhaustivamente (ver
 * task-p5t4-report.md): los únicos dos con las columnas necesarias (municipio,
 * producto, precio) son "precio mes combustible" (gjy9-tpph, congelado en dic-2022) y
 * "Precios Combustibles (2017-1)" (x6id-4v3g, congelado en 2018) — ambos muy por fuera
 * del umbral de 6 meses. Los datasets "AUTOMATIZADO" vigentes de MinEnergía/SICOM en ese
 * portal cubren GNCV (gas vehicular) y volúmenes despachados, no precio de gasolina/ACPM
 * al público. Por eso el precio de combustible en RevScope es 100% manual — sin
 * `FuelPriceUpdater` ni botón "Actualizar precios en línea".
 */
object FuelPricePrefs {
    const val DEFAULT_CORRIENTE = 16_000.0
    const val DEFAULT_EXTRA = 20_000.0
    const val DEFAULT_DIESEL = 10_500.0

    /**
     * Lee los 3 precios vigentes, migrando la key legada [PreferencesKeys.FUEL_PRICE_COP_PER_GALLON]
     * a [PreferencesKeys.FUEL_PRICE_CORRIENTE] la primera vez que se detecta. Idempotente
     * y segura ante llamadas concurrentes desde distintos módulos (SessionAggregator,
     * SettingsViewModel) — cada una repite la misma migración sin efecto si ya ocurrió.
     */
    suspend fun read(settings: DataStore<Preferences>): FuelPrices {
        migrateLegacyIfNeeded(settings)
        val prefs = settings.data.first()
        return FuelPrices(
            corriente = prefs[PreferencesKeys.FUEL_PRICE_CORRIENTE] ?: DEFAULT_CORRIENTE,
            extra = prefs[PreferencesKeys.FUEL_PRICE_EXTRA] ?: DEFAULT_EXTRA,
            diesel = prefs[PreferencesKeys.FUEL_PRICE_DIESEL] ?: DEFAULT_DIESEL,
        )
    }

    /** Precio aplicable para [fuelType] ("CORRIENTE" | "EXTRA" | "DIESEL"); null/desconocido cae a corriente. */
    fun priceFor(prices: FuelPrices, fuelType: String?): Double = when (fuelType) {
        "EXTRA" -> prices.extra
        "DIESEL" -> prices.diesel
        else -> prices.corriente
    }

    private suspend fun migrateLegacyIfNeeded(settings: DataStore<Preferences>) {
        val prefs = settings.data.first()
        val legacy = prefs[PreferencesKeys.FUEL_PRICE_COP_PER_GALLON] ?: return
        if (prefs[PreferencesKeys.FUEL_PRICE_CORRIENTE] != null) {
            settings.edit { it.remove(PreferencesKeys.FUEL_PRICE_COP_PER_GALLON) }
            return
        }
        settings.edit {
            it[PreferencesKeys.FUEL_PRICE_CORRIENTE] = legacy
            it.remove(PreferencesKeys.FUEL_PRICE_COP_PER_GALLON)
        }
    }
}
