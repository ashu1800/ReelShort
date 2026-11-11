package com.reelshort.backend.content;

/**
 * 后台视频库状态。
 *
 * @param active 定时抓取是否开启（reelshort.content.library.enabled）
 */
public record ContentLibraryStatusResponse(
		long bookCount,
		long episodeCacheCount,
		long videoCacheCount,
		int nextPage,
		int totalPages,
		String lastCrawledAt,
		String lastError,
		int lastAdded,
		boolean active) {
}
