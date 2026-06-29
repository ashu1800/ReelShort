package com.reelshort.app.session

import com.reelshort.app.data.SavedCredentials

class InMemoryCredentialStore(initialCredentials: SavedCredentials? = null) : CredentialStore {
    private var credentials: SavedCredentials? = initialCredentials

    override suspend fun loadCredentials(): SavedCredentials? = credentials

    override suspend fun saveCredentials(credentials: SavedCredentials) {
        this.credentials = credentials
    }

    override suspend fun clearCredentials() {
        credentials = null
    }
}
