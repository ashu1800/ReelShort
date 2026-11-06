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
	 * 公平模式：返回"十分位"单位的整数（1.3 分 → 13），最少 1（=0.1 分）。
	 * 该值可直接传给 PointAccount.addTenths() 和 DailyEarningQuota.allocateTenths()。
	 */
	static int pointsForDurationTenths(int durationSeconds, int secondsPerPoint) {
		if (durationSeconds <= 0 || secondsPerPoint <= 0) {
			throw new IllegalArgumentException("duration and seconds per point must be positive");
		}
		return Math.max(1, (int) Math.round((double) durationSeconds / secondsPerPoint * FAIR_MODE_SCALE));
	}

	static int effectiveDailyLimit(int baseMaximum, int fluctuationPercent) {
		if (baseMaximum < 0 || fluctuationPercent < 0 || fluctuationPercent > 100) {
			throw new IllegalArgumentException("invalid daily earning limit configuration");
		}
		return (int) ((long) baseMaximum * (100 - fluctuationPercent) / 100);
	}

	/**
	 * 平滑每日速率：初始速率 = 1 / secondsPerPoint，随后随当日累计观看时长按幂函数递减，
	 * 并且积分曲线在达到 24 小时累计观看时刚好等于 effectiveMaximum。
	 *
	 * <p>令 r(t) = r0 * (1 - t / T)^k，其中 T = 24 小时；
	 * 要求积分曲线 C(T) = effectiveMaximum，得到 k = r0 * T / effectiveMaximum - 1。
	 * 返回区间内新增的十分位积分（10 = 1 分），由调用方再按每日剩余额度截断。
	 */
	static int progressivePointsTenths(int effectiveMaximum, int watchedSeconds, int durationSeconds,
			int secondsPerPoint) {
		if (effectiveMaximum <= 0 || durationSeconds <= 0 || secondsPerPoint <= 0) {
			return 0;
		}
		long daySeconds = 86_400L;
		double baseRate = 1.0 / secondsPerPoint; // 分/秒
		long watchedBefore = Math.max(0L, Math.min((long) watchedSeconds, daySeconds));
		long watchedAfter = Math.max(0L, Math.min((long) watchedSeconds + durationSeconds, daySeconds));
		if (watchedAfter <= watchedBefore) {
			return 0;
		}
		double totalAtDayEnd = baseRate * daySeconds;
		double requestedTenths;
		if (totalAtDayEnd <= effectiveMaximum) {
			// 配置的初始速率本身不足以在 24 小时内用完额度：保持线性，不额外抬高首小时奖励。
			requestedTenths = (watchedAfter - watchedBefore) * baseRate * FAIR_MODE_SCALE;
		}
		else {
			double k = totalAtDayEnd / effectiveMaximum - 1.0;
			double before = cumulative(effectiveMaximum, watchedBefore, k, daySeconds);
			double after = cumulative(effectiveMaximum, watchedAfter, k, daySeconds);
			requestedTenths = (after - before) * FAIR_MODE_SCALE;
		}
		return Math.max(1, (int) Math.round(requestedTenths));
	}

	private static double cumulative(double effectiveMaximum, long seconds, double k, long daySeconds) {
		double progress = seconds / (double) daySeconds;
		return effectiveMaximum * (1.0 - Math.pow(1.0 - progress, k + 1.0));
	}
}
