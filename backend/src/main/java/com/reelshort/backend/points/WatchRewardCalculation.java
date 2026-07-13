package com.reelshort.backend.points;

final class WatchRewardCalculation {

	private WatchRewardCalculation() {
	}

	static int pointsForDuration(int durationSeconds, int secondsPerPoint) {
		if (durationSeconds <= 0 || secondsPerPoint <= 0) {
			throw new IllegalArgumentException("duration and seconds per point must be positive");
		}
		return Math.max(1, durationSeconds / secondsPerPoint);
	}

	static int effectiveDailyLimit(int baseMaximum, int fluctuationPercent) {
		if (baseMaximum < 0 || fluctuationPercent < 0 || fluctuationPercent > 100) {
			throw new IllegalArgumentException("invalid daily earning limit configuration");
		}
		return (int) ((long) baseMaximum * (100 - fluctuationPercent) / 100);
	}
}
