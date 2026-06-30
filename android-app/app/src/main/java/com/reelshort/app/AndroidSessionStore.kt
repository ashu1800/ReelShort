package com.reelshort.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.reelshort.app.data.AuthSession
import com.reelshort.app.session.SessionStore

class AndroidSessionStore private constructor(
    private val preferences: SharedPreferences?,
    private val fallback: SessionStore,
) : SessionStore {
    override suspend fun loadSession(): AuthSession? {
        val prefs = preferences ?: return fallback.loadSession()
        return runCatching {
            val username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val tokenType = prefs.getString(KEY_TOKEN_TYPE, null)?.takeIf { it.isNotBlank() } ?: "Bearer"
            AuthSession(username = username, token = token, tokenType = tokenType)
        }.getOrNull()
    }

    override suspend fun saveSession(session: AuthSession) {
        val prefs = preferences
        if (prefs == null) {
            fallback.saveSession(session)
            return
        }
        prefs.edit()
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_TOKEN_TYPE, session.tokenType)
            .apply()
    }

    override suspend fun clearSession() {
        preferences?.edit()?.clear()?.apply()
        fallback.clearSession()
    }

    companion object {
        private const val PREFS_NAME = "reelshort-session"
        private const val KEY_USERNAME = "username"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_TYPE = "tokenType"

        fun create(context: Context, fallback: SessionStore): AndroidSessionStore {
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
            return AndroidSessionStore(preferences, fallback)
        }
    }
}
