package com.reelshort.backend.watch;

import java.util.List;

import com.reelshort.backend.points.WatchRewardStatus;

public record WatchEpisodeSnapshotResponse(
		String bookId,
		int episodeNum,
		int positionSeconds,
		int durationSeconds,
		int progressPercent,
		List<Integer> awardedStages,
		int awardedPoints,
		boolean rewardClaimed,
		WatchRewardStatus rewardStatus) {

	public static WatchEpisodeSnapshotResponse empty(String bookId, int episodeNum) {
		return new WatchEpisodeSnapshotResponse(bookId, episodeNum, 0, 0, 0, List.of(), 0, false,
				WatchRewardStatus.NOT_COMPLETE);
	}
}
