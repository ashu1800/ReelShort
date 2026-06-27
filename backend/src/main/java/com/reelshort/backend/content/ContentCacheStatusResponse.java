package com.reelshort.backend.content;

import java.util.List;

public record ContentCacheStatusResponse(
		long bookCount,
		long episodeCacheCount,
		List<ShelfStatus> shelves) {

	public record ShelfStatus(
			String shelfType,
			int itemCount,
			String refreshedAt,
			String lastError) {
	}
}
