package com.reelshort.backend.content;

import java.util.List;

public record ContentCacheStatusResponse(
		long bookCount,
		long episodeCacheCount,
		long videoCacheCount,
		List<ShelfStatus> shelves,
		List<RefreshRunStatus> recentRefreshRuns) {

	public record ShelfStatus(
			String shelfType,
			String locale,
			int itemCount,
			String refreshedAt,
			String lastError,
			String health,
			String healthMessage) {
	}

	public record RefreshRunStatus(
			String triggerSource,
			String shelfType,
			String locale,
			String status,
			String startedAt,
			String finishedAt,
			long durationMillis,
			int itemCount,
			String errorMessage) {
	}
}
