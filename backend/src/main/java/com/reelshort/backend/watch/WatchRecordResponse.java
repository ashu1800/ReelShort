package com.reelshort.backend.watch;

import java.util.List;
import java.util.UUID;

import com.reelshort.backend.points.WatchRewardResult;
import com.reelshort.backend.points.WatchRewardStatus;

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
		String updatedAt,
		boolean rewardClaimed,
		WatchRewardStatus rewardStatus) {

	public static WatchRecordResponse from(WatchRecord record) {
		return from(record, List.of(), 0, false, WatchRewardStatus.NOT_COMPLETE);
	}

	public static WatchRecordResponse from(WatchRecord record, List<Integer> awardedStages, int awardedPoints) {
		return from(record, awardedStages, awardedPoints, !awardedStages.isEmpty(),
				awardedStages.isEmpty() ? WatchRewardStatus.NOT_COMPLETE : WatchRewardStatus.AWARDED);
	}

	public static WatchRecordResponse from(WatchRecord record, WatchRewardResult reward) {
		return from(record, reward.awardedStages(), reward.awardedPoints(), reward.rewardClaimed(), reward.rewardStatus());
	}

	public static WatchRecordResponse fromClaim(WatchRecord record, int awardedPoints) {
		return from(record, List.of(), awardedPoints, true, WatchRewardStatus.ALREADY_CLAIMED);
	}

	private static WatchRecordResponse from(WatchRecord record, List<Integer> awardedStages, int awardedPoints,
			boolean rewardClaimed, WatchRewardStatus rewardStatus) {
		return new WatchRecordResponse(record.id(), record.bookId(), record.bookTitle(), record.filteredTitle(),
				record.episodeNum(), record.chapterId(), record.positionSeconds(), record.durationSeconds(),
				record.progressPercent(), awardedStages, awardedPoints, record.updatedAt().toString(), rewardClaimed,
				rewardStatus);
	}
}
