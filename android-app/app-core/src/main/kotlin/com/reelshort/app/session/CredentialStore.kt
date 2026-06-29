package com.reelshort.app.session

import com.reelshort.app.data.SavedCredentials

interface CredentialStore {
    suspend fun loadCredentials(): SavedCredentials?

    suspend fun saveCredentials(credentials: SavedCredentials)

    suspend fun clearCredentials()
}
