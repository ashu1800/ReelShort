package com.reelshort.backend.points;

import java.util.List;

public record WatchRewardResult(
		List<Integer> awardedStages,
		int awardedPoints,
		boolean rewardClaimed,
		WatchRewardStatus rewardStatus) {

	public static WatchRewardResult none() {
		return new WatchRewardResult(List.of(), 0, false, WatchRewardStatus.NOT_COMPLETE);
	}

	public static WatchRewardResult durationUnavailable() {
		return new WatchRewardResult(List.of(), 0, false, WatchRewardStatus.DURATION_UNAVAILABLE);
	}

	public static WatchRewardResult noVip() {
		return new WatchRewardResult(List.of(), 0, false, WatchRewardStatus.NOT_VIP);
	}
}
