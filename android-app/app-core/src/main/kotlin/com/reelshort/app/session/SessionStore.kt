package com.reelshort.app.session

import com.reelshort.app.data.AuthSession

interface SessionStore {
    suspend fun loadSession(): AuthSession?

    suspend fun saveSession(session: AuthSession)

    suspend fun clearSession()
}
