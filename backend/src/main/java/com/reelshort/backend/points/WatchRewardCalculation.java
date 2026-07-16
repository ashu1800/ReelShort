package com.reelshort.backend.points;

final class WatchRewardCalculation {

	static final int FAIR_MODE_SCALE = 10;

	private WatchRewardCalculation() {
	}

	static int pointsForDuration(int durationSeconds, int secondsPerPoint) {
		if (durationSeconds <= 0 || secondsPerPoint <= 0) {
			throw new IllegalArgumentException("duration and seconds per point must be positive");
		}
		return Math.max(1, durationSeconds / secondsPerPoint);
	}

	/**
	 * Fair mode: returns points scaled by 10 (e.g. 1.3 points → 13). The minimum is 10 (=1.0 points).
	 */
	static int pointsForDurationFair(int durationSeconds, int secondsPerPoint) {
		if (durationSeconds <= 0 || secondsPerPoint <= 0) {
			throw new IllegalArgumentException("duration and seconds per point must be positive");
		}
		return Math.max(FAIR_MODE_SCALE, (int) Math.round((double) durationSeconds / secondsPerPoint * FAIR_MODE_SCALE));
	}

	/**
	 * Converts a fair-mode internal value to display value (divide by 10, floor).
	 */
	static int toDisplay(int internalValue, boolean fairMode) {
		return fairMode ? internalValue / FAIR_MODE_SCALE : internalValue;
	}

	static int effectiveDailyLimit(int baseMaximum, int fluctuationPercent) {
		if (baseMaximum < 0 || fluctuationPercent < 0 || fluctuationPercent > 100) {
			throw new IllegalArgumentException("invalid daily earning limit configuration");
		}
		return (int) ((long) baseMaximum * (100 - fluctuationPercent) / 100);
	}
}
