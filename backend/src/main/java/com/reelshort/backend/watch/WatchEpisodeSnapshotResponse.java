package com.reelshort.backend.watch;

import java.util.List;

public record WatchEpisodeSnapshotResponse(
		String bookId,
		int episodeNum,
		int positionSeconds,
		int durationSeconds,
		int progressPercent,
		List<Integer> awardedStages) {

	public static WatchEpisodeSnapshotResponse empty(String bookId, int episodeNum) {
		return new WatchEpisodeSnapshotResponse(bookId, episodeNum, 0, 0, 0, List.of());
	}
}
