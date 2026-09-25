package com.revscope.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.revscope.core.data.datastore.PreferencesKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreferencesBackupCodecTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val riderName = stringPreferencesKey("server_rider_name")
    private val secretKeys = listOf(
        PreferencesKeys.CLAUDE_API_KEY,
        PreferencesKeys.MCP_TOKEN,
        PreferencesKeys.SERVER_AUTH_TOKEN,
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(tmp.root, "test_${System.nanoTime()}.preferences_pb")
        }

    private fun backupJson(vararg entries: Pair<String, String>): String {
        val root = JSONObject()
        entries.forEach { (name, value) ->
            root.put(name, JSONObject().put("type", "string").put("value", value))
        }
        return root.toString()
    }

    @Test
    fun `encode no exporta los tokens ni la key de Claude`() {
        val prefs = preferencesOf(
            PreferencesKeys.MCP_TOKEN to "mcp-secreto",
            PreferencesKeys.SERVER_AUTH_TOKEN to "server-secreto",
            PreferencesKeys.CLAUDE_API_KEY to "sk-ant-secreto",
            riderName to "Santi",
        )

        val json = JSONObject(PreferencesBackupCodec.encode(prefs))

        secretKeys.forEach { assertFalse(it.name, json.has(it.name)) }
        assertEquals("Santi", json.getJSONObject(riderName.name).getString("value"))
    }

    @Test
    fun `restore conserva los tokens locales e ignora los del backup`() = runTest {
        val settings = newDataStore()
        settings.edit {
            it[PreferencesKeys.MCP_TOKEN] = "mcp-local"
            it[PreferencesKeys.SERVER_AUTH_TOKEN] = "server-local"
            it[riderName] = "Local"
            it[intPreferencesKey("solo_local")] = 7
        }
        val json = backupJson(
            PreferencesKeys.MCP_TOKEN.name to "mcp-ajeno",
            PreferencesKeys.SERVER_AUTH_TOKEN.name to "server-ajeno",
            riderName.name to "Del backup",
        )

        PreferencesBackupCodec.restore(json, settings)

        val restored = settings.data.first()
        assertEquals("mcp-local", restored[PreferencesKeys.MCP_TOKEN])
        assertEquals("server-local", restored[PreferencesKeys.SERVER_AUTH_TOKEN])
        assertEquals("Del backup", restored[riderName])
        assertNull(restored[intPreferencesKey("solo_local")])
    }

    @Test
    fun `restore no importa tokens del backup si el dispositivo no tenia`() = runTest {
        val settings = newDataStore()
        val json = backupJson(
            PreferencesKeys.MCP_TOKEN.name to "mcp-ajeno",
            PreferencesKeys.SERVER_AUTH_TOKEN.name to "server-ajeno",
            PreferencesKeys.CLAUDE_API_KEY.name to "sk-ant-ajena",
        )

        PreferencesBackupCodec.restore(json, settings)

        val restored = settings.data.first()
        secretKeys.forEach { assertNull(it.name, restored[it]) }
    }

    @Test
    fun `ida y vuelta conserva todos los tipos`() = runTest {
        val original = preferencesOf(
            booleanPreferencesKey("b") to true,
            intPreferencesKey("i") to 42,
            longPreferencesKey("l") to 9_000_000_000L,
            floatPreferencesKey("f") to 0.1f,
            doublePreferencesKey("d") to 3.141592653589793,
            stringPreferencesKey("s") to "ñandú",
        )
        val settings = newDataStore()

        PreferencesBackupCodec.restore(PreferencesBackupCodec.encode(original), settings)

        val restored = settings.data.first()
        assertEquals(original.asMap(), restored.asMap())
    }
}
