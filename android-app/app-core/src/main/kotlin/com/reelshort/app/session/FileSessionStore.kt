package com.reelshort.app.session

import com.reelshort.app.data.AuthSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class FileSessionStore(
    private val sessionFile: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionStore {
    private val tempFile: File
        get() = File(sessionFile.parentFile, "${sessionFile.name}.tmp")

    override suspend fun loadSession(): AuthSession? {
        if (!sessionFile.exists()) {
            return null
        }
        return try {
            json.decodeFromString(SessionDto.serializer(), sessionFile.readText()).toAuthSession()
        } catch (exception: IOException) {
            deleteSessionFile()
            null
        } catch (exception: IllegalArgumentException) {
            deleteSessionFile()
            null
        } catch (exception: SerializationException) {
            deleteSessionFile()
            null
        }
    }

    override suspend fun saveSession(session: AuthSession) {
        sessionFile.parentFile?.mkdirs()
        val dto = SessionDto.from(session)
        val encoded = json.encodeToString(SessionDto.serializer(), dto)
        val temp = tempFile
        temp.writeText(encoded)
        if (sessionFile.exists() && !sessionFile.delete()) {
            temp.delete()
            throw IOException("failed to replace session file")
        }
        if (!temp.renameTo(sessionFile)) {
            temp.delete()
            throw IOException("failed to persist session file")
        }
    }

    override suspend fun clearSession() {
        deleteSessionFile()
    }

    private fun deleteSessionFile() {
        if (sessionFile.exists()) {
            sessionFile.delete()
        }
        val temp = tempFile
        if (temp.exists()) {
            temp.delete()
        }
    }

    @Serializable
    private data class SessionDto(
        val username: String,
        val token: String,
        val tokenType: String,
    ) {
        fun toAuthSession(): AuthSession =
            AuthSession(username = username, token = token, tokenType = tokenType)

        companion object {
            fun from(session: AuthSession): SessionDto =
                SessionDto(
                    username = session.username,
                    token = session.token,
                    tokenType = session.tokenType,
                )
        }
    }
}
