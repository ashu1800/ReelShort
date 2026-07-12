package com.reelshort.app.session

import java.io.File

object FailClosedSessionStoreFactory {
    fun create(
        secureStore: SessionStore?,
        legacyPlaintextFile: File,
    ): SessionStore {
        check(!legacyPlaintextFile.exists() || legacyPlaintextFile.delete()) {
            "Unable to remove legacy plaintext session"
        }
        return secureStore ?: InMemorySessionStore()
    }
}
