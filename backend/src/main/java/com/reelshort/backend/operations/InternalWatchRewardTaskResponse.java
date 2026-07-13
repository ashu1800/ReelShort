package com.reelshort.backend.operations;

import java.util.List;

public record InternalWatchRewardTaskResponse(
		String bookId,
		String bookTitle,
		String bookDescription,
		String filteredTitle,
		int episodeNum,
		String chapterId,
		String episodeTitle,
		int durationSeconds,
		int currentProgressPercent,
		Integer nextRewardStage,
		Integer targetProgressPercent,
		List<Integer> alreadyClaimedStages,
		boolean canReport,
		int estimatedRewardPoints,
		int dailyEffectiveLimit,
		int dailyEarnedPoints,
		int dailyRemainingPoints,
		boolean rewardClaimed) {
}
