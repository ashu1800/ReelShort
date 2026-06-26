package com.reelshort.backend.points;

import java.util.List;

public record WatchRewardResult(List<Integer> awardedStages, int awardedPoints) {

	public static WatchRewardResult none() {
		return new WatchRewardResult(List.of(), 0);
	}
}
