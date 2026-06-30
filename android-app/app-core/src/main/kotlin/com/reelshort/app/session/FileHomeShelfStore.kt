package com.reelshort.app.session

import com.reelshort.app.data.BookSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * 首页货架数据的文件持久化实现。
 *
 * 沿用 [FileSessionStore] 的两个关键模式：
 * - 原子写：先写临时文件再 rename，避免进程崩溃留下半写文件。
 * - 自愈读：文件缺失或损坏时返回空列表并删除文件，不抛错阻断启动。
 */
class FileHomeShelfStore(
    private val cacheFile: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : HomeShelfStore {
    private val tempFile: File
        get() = File(cacheFile.parentFile, "${cacheFile.name}.tmp")

    override suspend fun loadHomeShelf(): List<BookSummary> {
        if (!cacheFile.exists()) {
            return emptyList()
        }
        return try {
            json.decodeFromString(ListSerializer(BookSummaryDto.serializer()), cacheFile.readText())
                .map { it.toBookSummary() }
        } catch (exception: IOException) {
            deleteCacheFile()
            emptyList()
        } catch (exception: IllegalArgumentException) {
            deleteCacheFile()
            emptyList()
        } catch (exception: SerializationException) {
            deleteCacheFile()
            emptyList()
        }
    }

    override suspend fun saveHomeShelf(shelf: List<BookSummary>) {
        cacheFile.parentFile?.mkdirs()
        val dtos = shelf.map { BookSummaryDto.from(it) }
        val encoded = json.encodeToString(ListSerializer(BookSummaryDto.serializer()), dtos)
        val temp = tempFile
        temp.writeText(encoded)
        if (cacheFile.exists() && !cacheFile.delete()) {
            temp.delete()
            throw IOException("failed to replace home shelf cache file")
        }
        if (!temp.renameTo(cacheFile)) {
            temp.delete()
            throw IOException("failed to persist home shelf cache file")
        }
    }

    override suspend fun clearHomeShelf() {
        deleteCacheFile()
    }

    private fun deleteCacheFile() {
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        val temp = tempFile
        if (temp.exists()) {
            temp.delete()
        }
    }

    @Serializable
    private data class BookSummaryDto(
        val id: String,
        val title: String,
        val filteredTitle: String,
        val coverUrl: String? = null,
        val description: String,
        val chapterCount: Int,
    ) {
        fun toBookSummary(): BookSummary =
            BookSummary(
                id = id,
                title = title,
                filteredTitle = filteredTitle,
                coverUrl = coverUrl,
                description = description,
                chapterCount = chapterCount,
            )

        companion object {
            fun from(book: BookSummary): BookSummaryDto =
                BookSummaryDto(
                    id = book.id,
                    title = book.title,
                    filteredTitle = book.filteredTitle,
                    coverUrl = book.coverUrl,
                    description = book.description,
                    chapterCount = book.chapterCount,
                )
        }
    }
}
