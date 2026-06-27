package com.reelshort.app.session

import com.reelshort.app.data.AuthSession
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSessionStoreTest {
    @Test
    fun saveSessionPersistsJsonForNewStoreInstance() = runTest {
        val sessionFile = tempSessionFile()
        val session = AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer")

        FileSessionStore(sessionFile).saveSession(session)
        val restored = FileSessionStore(sessionFile).loadSession()

        assertEquals(session, restored)
        assertTrue(sessionFile.exists())
    }

    @Test
    fun clearSessionDeletesStoredSessionAndTempFile() = runTest {
        val sessionFile = tempSessionFile()
        val tempFile = File(sessionFile.parentFile, "${sessionFile.name}.tmp")
        val store = FileSessionStore(sessionFile)
        store.saveSession(AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer"))
        tempFile.writeText("stale")

        store.clearSession()

        assertNull(store.loadSession())
        assertFalse(sessionFile.exists())
        assertFalse(tempFile.exists())
    }

    @Test
    fun loadSessionReturnsNullWhenFileDoesNotExist() = runTest {
        assertNull(FileSessionStore(tempSessionFile()).loadSession())
    }

    @Test
    fun loadSessionReturnsNullForCorruptJson() = runTest {
        val sessionFile = tempSessionFile()
        sessionFile.writeText("{not json")

        assertNull(FileSessionStore(sessionFile).loadSession())
        assertFalse(sessionFile.exists())
    }

    private fun tempSessionFile(): File {
        val directory = createTempDirectory(prefix = "reelshort-session-store-test").toFile()
        return File(directory, "session.json")
    }
}
