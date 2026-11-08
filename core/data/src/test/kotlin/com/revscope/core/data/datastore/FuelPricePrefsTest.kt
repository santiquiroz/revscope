package com.revscope.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.common.i18n.Region
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FuelPricePrefsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(tmp.root, "test_${System.nanoTime()}.preferences_pb")
        }

    @Test
    fun `US sin nada guardado no tiene precios`() = runTest {
        assertEquals(FuelPrices(null, null, null), FuelPricePrefs.read(newDataStore(), Region.US))
    }

    @Test
    fun `CO sin nada guardado usa los precios por defecto de hoy`() = runTest {
        assertEquals(FuelPrices(16_000.0, 20_000.0, 10_500.0), FuelPricePrefs.read(newDataStore(), Region.CO))
    }

    @Test
    fun `fuera de CO un precio guardado se respeta y los demas siguen null`() = runTest {
        val store = newDataStore()
        store.edit { it[PreferencesKeys.FUEL_PRICE_CORRIENTE] = 5.6 }

        val prices = FuelPricePrefs.read(store, Region.GB)

        assertEquals(5.6, prices.corriente!!, 1e-9)
        assertNull(prices.extra)
        assertNull(FuelPricePrefs.priceFor(prices, "DIESEL"))
    }

    @Test
    fun `la key legada se migra a corriente`() = runTest {
        val store = newDataStore()
        store.edit { it[PreferencesKeys.FUEL_PRICE_COP_PER_GALLON] = 15_000.0 }

        val prices = FuelPricePrefs.read(store, Region.CO)

        assertEquals(15_000.0, prices.corriente!!, 1e-9)
        assertNull(store.data.first()[PreferencesKeys.FUEL_PRICE_COP_PER_GALLON])
    }
}
