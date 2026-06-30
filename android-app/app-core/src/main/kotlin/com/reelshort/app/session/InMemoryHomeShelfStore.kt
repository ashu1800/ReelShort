package com.reelshort.app.session

import com.reelshort.app.data.BookSummary

class InMemoryHomeShelfStore(initialShelf: List<BookSummary> = emptyList()) : HomeShelfStore {
    private var shelf: List<BookSummary> = initialShelf

    override suspend fun loadHomeShelf(): List<BookSummary> = shelf

    override suspend fun saveHomeShelf(shelf: List<BookSummary>) {
        this.shelf = shelf
    }

    override suspend fun clearHomeShelf() {
        shelf = emptyList()
    }
}
