package com.revscope.core.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE = "revscope_secure"
private const val KEY_CLAUDE_API = "claude_api_key"

/**
 * Hardware-backed encrypted storage for secrets (Android Keystore via Jetpack
 * Security). The Claude API key lived in plain DataStore before — reads fall
 * back there is handled by callers during migration.
 *
 * All methods do disk/keystore I/O — call from Dispatchers.IO.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Corrupt keystore entry (backup restore, etc.) — degrade to no secret storage
            Timber.e(e, "SecureKeyStore: init failed")
            null
        }
    }

    fun getClaudeApiKey(): String? = prefs?.getString(KEY_CLAUDE_API, null)

    fun setClaudeApiKey(value: String?) {
        prefs?.edit()?.apply {
            if (value.isNullOrBlank()) remove(KEY_CLAUDE_API) else putString(KEY_CLAUDE_API, value)
        }?.apply()
    }
}
