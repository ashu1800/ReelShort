package com.reelshort.backend.content;

public record ContentBook(
		String bookId,
		String title,
		String filteredTitle,
		String coverUrl,
		int chapterCount) {
}

