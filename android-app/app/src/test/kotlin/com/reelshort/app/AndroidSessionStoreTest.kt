package com.reelshort.app

import com.reelshort.app.session.InMemorySessionStore
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AndroidSessionStoreTest {
    @Test
    fun secureStoreInitializationFailureUsesEmptyMemoryStoreAndDeletesLegacyPlaintext() {
        val legacyFile = legacySessionFile().apply { writeText("plaintext-token") }

        val store = createFailClosedAndroidSessionStore(legacyFile) {
            error("Keystore unavailable")
        }

        assertFalse(legacyFile.exists())
        assertIs<InMemorySessionStore>(store)
    }

    private fun legacySessionFile(): File =
        createTempDirectory(prefix = "android-session-test").resolve("reelshort-session.json").toFile()
}
