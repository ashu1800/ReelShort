package com.reelshort.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.reelshort.app.data.SavedCredentials
import com.reelshort.app.session.CredentialStore

class AndroidCredentialStore private constructor(
    private val preferences: SharedPreferences?,
) : CredentialStore {
    override suspend fun loadCredentials(): SavedCredentials? {
        val prefs = preferences ?: return null
        return runCatching {
            val rememberPassword = prefs.getBoolean(KEY_REMEMBER_PASSWORD, false)
            if (!rememberPassword) {
                return@runCatching null
            }
            val username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val password = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotBlank() } ?: return@runCatching null
            SavedCredentials(
                username = username,
                password = password,
                rememberPassword = true,
            )
        }.getOrNull()
    }

    override suspend fun saveCredentials(credentials: SavedCredentials) {
        runCatching {
            preferences?.edit()
                ?.putString(KEY_USERNAME, credentials.username)
                ?.putString(KEY_PASSWORD, credentials.password)
                ?.putBoolean(KEY_REMEMBER_PASSWORD, credentials.rememberPassword)
                ?.apply()
        }
    }

    override suspend fun clearCredentials() {
        runCatching {
            preferences?.edit()?.clear()?.apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "reelshort-credentials"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER_PASSWORD = "rememberPassword"

        fun create(context: Context): AndroidCredentialStore {
            val preferences = runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
            return AndroidCredentialStore(preferences)
        }
    }
}
