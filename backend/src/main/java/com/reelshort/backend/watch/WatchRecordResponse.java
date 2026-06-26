package com.reelshort.backend.watch;

import java.util.UUID;

public record WatchRecordResponse(
		UUID id,
		String bookId,
		String bookTitle,
		String filteredTitle,
		int episodeNum,
		String chapterId,
		int positionSeconds,
		int durationSeconds,
		int progressPercent,
		String updatedAt) {

	public static WatchRecordResponse from(WatchRecord record) {
		return new WatchRecordResponse(record.id(), record.bookId(), record.bookTitle(), record.filteredTitle(),
				record.episodeNum(), record.chapterId(), record.positionSeconds(), record.durationSeconds(),
				record.progressPercent(), record.updatedAt().toString());
	}
}
