package com.reelshort.app.session

import com.reelshort.app.data.AuthSession

class InMemorySessionStore(initialSession: AuthSession? = null) : SessionStore {
    private var session: AuthSession? = initialSession

    override suspend fun loadSession(): AuthSession? = session

    override suspend fun saveSession(session: AuthSession) {
        this.session = session
    }

    override suspend fun clearSession() {
        session = null
    }
}
