package com.reelshort.backend.operations;

import java.util.List;

import com.reelshort.backend.points.PointAccountResponse;
import com.reelshort.backend.points.WatchRewardStatus;
import com.reelshort.backend.watch.WatchRecordResponse;

public record InternalWatchProgressResponse(
		String bookId,
		int episodeNum,
		int progressPercent,
		List<Integer> awardedStages,
		int awardedPoints,
		int balance,
		int frozenPoints,
		int availablePoints,
		boolean rewardClaimed,
		WatchRewardStatus rewardStatus) {

	static InternalWatchProgressResponse from(WatchRecordResponse watchRecord, PointAccountResponse account) {
		return new InternalWatchProgressResponse(watchRecord.bookId(), watchRecord.episodeNum(),
				watchRecord.progressPercent(), watchRecord.awardedStages(), watchRecord.awardedPoints(),
				account.balance(), account.frozenPoints(), account.availablePoints(), watchRecord.rewardClaimed(),
				watchRecord.rewardStatus());
	}
}
