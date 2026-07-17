package com.reelshort.backend.points;

final class WatchRewardCalculation {

	/** 十分位基数（1 分 = 10 个十分位）。用于观看奖励的定点小数累积。 */
	static final int FAIR_MODE_SCALE = 10;

	private WatchRewardCalculation() {
	}

	/**
	 * 普通模式：返回整数积分（向下取整，最少 1）。
	 */
	static int pointsForDuration(int durationSeconds, int secondsPerPoint) {
		if (durationSeconds <= 0 || secondsPerPoint <= 0) {
			throw new IllegalArgumentException("duration and seconds per point must be positive");
		}
		return Math.max(1, durationSeconds / secondsPerPoint);
	}

	/**
	 * 公平模式：返回"十分位"单位的整数（1.3 分 → 13），最少 10（=1.0 分）。
	 * 该值可直接传给 PointAccount.addTenths() 和 DailyEarningQuota.allocateTenths()。
	 */
	static int pointsForDurationTenths(int durationSeconds, int secondsPerPoint) {
		if (durationSeconds <= 0 || secondsPerPoint <= 0) {
			throw new IllegalArgumentException("duration and seconds per point must be positive");
		}
		return Math.max(FAIR_MODE_SCALE, (int) Math.round((double) durationSeconds / secondsPerPoint * FAIR_MODE_SCALE));
	}

	static int effectiveDailyLimit(int baseMaximum, int fluctuationPercent) {
		if (baseMaximum < 0 || fluctuationPercent < 0 || fluctuationPercent > 100) {
			throw new IllegalArgumentException("invalid daily earning limit configuration");
		}
		return (int) ((long) baseMaximum * (100 - fluctuationPercent) / 100);
	}
}
