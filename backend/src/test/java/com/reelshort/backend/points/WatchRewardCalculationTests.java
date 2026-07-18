package com.reelshort.backend.points;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WatchRewardCalculationTests {

	@Test
	void calculatesOnePointForShortAndPartialMinuteVideos() {
		assertThat(WatchRewardCalculation.pointsForDuration(1, 60)).isEqualTo(1);
		assertThat(WatchRewardCalculation.pointsForDuration(59, 60)).isEqualTo(1);
		assertThat(WatchRewardCalculation.pointsForDuration(60, 60)).isEqualTo(1);
		assertThat(WatchRewardCalculation.pointsForDuration(61, 60)).isEqualTo(1);
		assertThat(WatchRewardCalculation.pointsForDuration(119, 60)).isEqualTo(1);
		assertThat(WatchRewardCalculation.pointsForDuration(120, 60)).isEqualTo(2);
	}

	@Test
	void calculatesDailyLimitWithDownwardFluctuation() {
		assertThat(WatchRewardCalculation.effectiveDailyLimit(1000, 29)).isEqualTo(710);
		assertThat(WatchRewardCalculation.effectiveDailyLimit(1000, 15)).isEqualTo(850);
		assertThat(WatchRewardCalculation.effectiveDailyLimit(1000, 35)).isEqualTo(650);
	}

	@Test
	void fairModeReturnsTenthsWithOneDecimalPrecision() {
		// 60 秒 / 60 秒每分 = 1.0 分 → 10 个十分位
		assertThat(WatchRewardCalculation.pointsForDurationTenths(60, 60)).isEqualTo(10);
		// 78 秒 / 60 = 1.3 分 → 13 个十分位
		assertThat(WatchRewardCalculation.pointsForDurationTenths(78, 60)).isEqualTo(13);
		// 75 秒 / 60 = 1.25 → 四舍五入到 1.3 → 13
		assertThat(WatchRewardCalculation.pointsForDurationTenths(75, 60)).isEqualTo(13);
		// 90 秒 / 60 = 1.5 → 15 个十分位
		assertThat(WatchRewardCalculation.pointsForDurationTenths(90, 60)).isEqualTo(15);
		// 公平模式保留真实十分位，极短视频最少 0.1 分；普通模式仍保证最低 1 分。
		assertThat(WatchRewardCalculation.pointsForDurationTenths(1, 60)).isEqualTo(1);
		assertThat(WatchRewardCalculation.pointsForDurationTenths(5, 60)).isEqualTo(1);
		assertThat(WatchRewardCalculation.pointsForDurationTenths(42, 60)).isEqualTo(7);
	}
}
