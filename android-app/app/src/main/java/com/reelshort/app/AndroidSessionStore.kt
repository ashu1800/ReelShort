package com.reelshort.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.reelshort.app.data.AuthSession
import com.reelshort.app.session.FailClosedSessionStoreFactory
import com.reelshort.app.session.SessionStore
import java.io.File

internal fun createFailClosedAndroidSessionStore(
    legacyPlaintextFile: File,
    secureStoreFactory: () -> SessionStore,
): SessionStore = FailClosedSessionStoreFactory.create(
    secureStore = try {
        secureStoreFactory()
    } catch (_: Exception) {
        null
    },
    legacyPlaintextFile = legacyPlaintextFile,
)

class AndroidSessionStore private constructor(
    private val preferences: SharedPreferences,
) : SessionStore {
    override suspend fun loadSession(): AuthSession? {
        return try {
            val username = preferences.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
            val phoneE164 = preferences.getString(KEY_PHONE_E164, null)?.takeIf { it.isNotBlank() }
            val token = preferences.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
            val tokenType = preferences.getString(KEY_TOKEN_TYPE, null)?.takeIf { it.isNotBlank() } ?: "Bearer"
            AuthSession(username = username, token = token, tokenType = tokenType, phoneE164 = phoneE164)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun saveSession(session: AuthSession) {
        val editor = preferences.edit()
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_TOKEN_TYPE, session.tokenType)
        if (session.phoneE164.isNullOrBlank()) {
            editor.remove(KEY_PHONE_E164)
        } else {
            editor.putString(KEY_PHONE_E164, session.phoneE164)
        }
        editor.apply()
    }

    override suspend fun clearSession() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "reelshort-session"
        private const val KEY_USERNAME = "username"
        private const val KEY_PHONE_E164 = "phoneE164"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_TYPE = "tokenType"

        fun create(context: Context, legacyPlaintextFile: File): SessionStore {
            return createFailClosedAndroidSessionStore(legacyPlaintextFile) {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val preferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                AndroidSessionStore(preferences)
            }
        }
    }
}
