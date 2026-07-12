package com.reelshort.app.session

import com.reelshort.app.data.AuthSession
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFailsWith

class FailClosedSessionStoreFactoryTest {
    @Test
    fun encryptedStoreUnavailableUsesOnlyMemoryAndDeletesLegacyPlaintext() = runTest {
        val legacyFile = legacySessionFile()
        legacyFile.writeText("plaintext-token")

        val store = FailClosedSessionStoreFactory.create(
            secureStore = null,
            legacyPlaintextFile = legacyFile,
        )
        store.saveSession(AuthSession(username = "user", token = "secret", tokenType = "Bearer"))

        assertFalse(legacyFile.exists())
        assertNull(
            FailClosedSessionStoreFactory.create(
                secureStore = null,
                legacyPlaintextFile = legacyFile,
            ).loadSession(),
        )
    }

    @Test
    fun encryptedStoreAvailableIsUsedAndLegacyPlaintextIsDeleted() {
        val legacyFile = legacySessionFile()
        legacyFile.writeText("plaintext-token")
        val secureStore = InMemorySessionStore()

        val selectedStore = FailClosedSessionStoreFactory.create(
            secureStore = secureStore,
            legacyPlaintextFile = legacyFile,
        )

        assertSame(secureStore, selectedStore)
        assertFalse(legacyFile.exists())
    }

    @Test
    fun legacyPlaintextDeletionFailureThrowsBeforeReturningStore() {
        val undeletablePath = legacySessionFile().apply {
            mkdirs()
            resolve("token.txt").writeText("plaintext-token")
        }

        assertFailsWith<IllegalStateException> {
            FailClosedSessionStoreFactory.create(
                secureStore = InMemorySessionStore(),
                legacyPlaintextFile = undeletablePath,
            )
        }
    }

    private fun legacySessionFile() =
        createTempDirectory(prefix = "legacy-session-test").resolve("reelshort-session.json").toFile()
}
