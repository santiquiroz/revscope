package com.revscope.core.obd.mcp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * Enabled toggle + bearer token for the local-network MCP server (plan6 Task 4). The token is
 * generated once (UUID v4) the first time it's requested and persisted from then on — Settings
 * and [McpServerService] both read it through [tokenOrGenerate], never generating their own.
 */
class McpTokenStore @Inject constructor(private val settings: DataStore<Preferences>) {

    val enabled: Flow<Boolean> = settings.data.map { it[PreferencesKeys.MCP_SERVER_ENABLED] ?: false }

    val token: Flow<String?> = settings.data.map { it[PreferencesKeys.MCP_TOKEN] }

    suspend fun setEnabled(value: Boolean) {
        settings.edit { it[PreferencesKeys.MCP_SERVER_ENABLED] = value }
    }

    /** Returns the persisted token, generating and persisting a new UUID the first time. */
    suspend fun tokenOrGenerate(): String {
        val existing = settings.data.first()[PreferencesKeys.MCP_TOKEN]
        if (!existing.isNullOrBlank()) return existing
        val generated = generateToken()
        settings.edit { it[PreferencesKeys.MCP_TOKEN] = generated }
        return generated
    }

    companion object {
        fun generateToken(): String = UUID.randomUUID().toString()
    }
}
