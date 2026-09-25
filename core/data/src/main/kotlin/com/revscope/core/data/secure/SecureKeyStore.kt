package com.revscope.core.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE = "revscope_secure"
private const val KEY_CLAUDE_API = "claude_api_key"
private const val KEY_PREFIX_PROVIDER_API = "provider_api_key_"

/** Provider id constants — must match the literal values AiProviderFactory (:core:intelligence) uses. */
const val AI_PROVIDER_ANTHROPIC = "anthropic"

/**
 * Hardware-backed encrypted storage for secrets (Android Keystore via Jetpack
 * Security). Multi-provider API keys (Task 7, Plan 5): the pre-existing Claude key
 * keeps living under [KEY_CLAUDE_API] — that slot IS the "anthropic" provider, no data
 * migration needed — while every other provider gets its own `provider_api_key_<id>` entry.
 *
 * All methods do disk/keystore I/O — call from Dispatchers.IO.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // A restored backup brings the prefs file without its Keystore key: wipe it and start empty.
    private val prefs: SharedPreferences? by lazy {
        openWithRecovery(
            create = ::createEncryptedPrefs,
            wipe = { context.deleteSharedPreferences(PREFS_FILE) },
        )
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiKey(provider: String): String? = prefs?.getString(prefsKeyFor(provider), null)

    fun setApiKey(provider: String, value: String?): Boolean {
        val store = prefs ?: return false
        val key = prefsKeyFor(provider)
        store.edit().apply {
            if (value.isNullOrBlank()) remove(key) else putString(key, value)
        }.apply()
        return true
    }

    private fun prefsKeyFor(provider: String): String =
        if (provider == AI_PROVIDER_ANTHROPIC) KEY_CLAUDE_API else "$KEY_PREFIX_PROVIDER_API$provider"

    /** Compat shim for callers that predate multi-provider support. */
    fun getClaudeApiKey(): String? = getApiKey(AI_PROVIDER_ANTHROPIC)

    fun setClaudeApiKey(value: String?) = setApiKey(AI_PROVIDER_ANTHROPIC, value)
}
