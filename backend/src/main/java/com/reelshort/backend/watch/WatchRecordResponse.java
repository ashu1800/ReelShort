package com.reelshort.backend.watch;

import java.util.List;
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
		List<Integer> awardedStages,
		int awardedPoints,
		String updatedAt) {

	public static WatchRecordResponse from(WatchRecord record) {
		return from(record, List.of(), 0);
	}

	public static WatchRecordResponse from(WatchRecord record, List<Integer> awardedStages, int awardedPoints) {
		return new WatchRecordResponse(record.id(), record.bookId(), record.bookTitle(), record.filteredTitle(),
				record.episodeNum(), record.chapterId(), record.positionSeconds(), record.durationSeconds(),
				record.progressPercent(), awardedStages, awardedPoints, record.updatedAt().toString());
	}
}
