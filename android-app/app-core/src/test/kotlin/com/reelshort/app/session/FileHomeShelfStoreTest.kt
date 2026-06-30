package com.reelshort.app.session

import com.reelshort.app.data.BookSummary
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileHomeShelfStoreTest {
    @Test
    fun saveHomeShelfPersistsJsonForNewStoreInstance() = runTest {
        val cacheFile = tempCacheFile()
        val shelf = sampleShelf()

        FileHomeShelfStore(cacheFile).saveHomeShelf(shelf)
        val restored = FileHomeShelfStore(cacheFile).loadHomeShelf()

        assertEquals(shelf, restored)
        assertTrue(cacheFile.exists())
    }

    @Test
    fun clearHomeShelfDeletesStoredCacheAndTempFile() = runTest {
        val cacheFile = tempCacheFile()
        val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        val store = FileHomeShelfStore(cacheFile)
        store.saveHomeShelf(sampleShelf())
        tempFile.writeText("stale")

        store.clearHomeShelf()

        assertEquals(emptyList(), store.loadHomeShelf())
        assertFalse(cacheFile.exists())
        assertFalse(tempFile.exists())
    }

    @Test
    fun loadHomeShelfReturnsEmptyWhenFileDoesNotExist() = runTest {
        assertEquals(emptyList<BookSummary>(), FileHomeShelfStore(tempCacheFile()).loadHomeShelf())
    }

    @Test
    fun loadHomeShelfReturnsEmptyForCorruptJson() = runTest {
        val cacheFile = tempCacheFile()
        cacheFile.writeText("{not json")

        assertEquals(emptyList<BookSummary>(), FileHomeShelfStore(cacheFile).loadHomeShelf())
        assertFalse(cacheFile.exists())
    }

    @Test
    fun saveHomeShelfRoundTripsNullableCoverUrl() = runTest {
        val cacheFile = tempCacheFile()
        val shelf = listOf(
            BookSummary(
                id = "a",
                title = "标题",
                filteredTitle = "filtered",
                coverUrl = null,
                description = "简介",
                chapterCount = 10,
            ),
        )

        FileHomeShelfStore(cacheFile).saveHomeShelf(shelf)
        val restored = FileHomeShelfStore(cacheFile).loadHomeShelf()

        assertEquals(shelf, restored)
    }

    private fun sampleShelf(): List<BookSummary> = listOf(
        BookSummary(
            id = "book-1",
            title = "示例短剧",
            filteredTitle = "demo",
            coverUrl = "https://example.com/cover.jpg",
            description = "示例简介",
            chapterCount = 20,
        ),
        BookSummary(
            id = "book-2",
            title = "另一部",
            filteredTitle = "another",
            coverUrl = null,
            description = "",
            chapterCount = 5,
        ),
    )

    private fun tempCacheFile(): File {
        val directory = createTempDirectory(prefix = "reelshort-home-shelf-store-test").toFile()
        return File(directory, "home-shelf.json")
    }
}
