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
	void progressiveRateDecaysAndFillsDailyLimitExactlyAtTwentyFourHours() {
		// 初始速率 1 分/60 秒，按累计播放时长递减，24 小时刚好用完 1000 分。
		assertThat(WatchRewardCalculation.progressivePointsTenths(1000, 0, 86_400, 60)).isEqualTo(10_000);
		assertThat(WatchRewardCalculation.progressivePointsTenths(650, 0, 86_400, 60)).isEqualTo(6_500);
		// 同样配置下，后一小时拿到的积分应少于前一小时。
		int firstHour = WatchRewardCalculation.progressivePointsTenths(1000, 0, 3_600, 60);
		int secondHour = WatchRewardCalculation.progressivePointsTenths(1000, 3_600, 3_600, 60);
		assertThat(firstHour).isBetween(580, 610);
		assertThat(secondHour).isLessThan(firstHour);
		// 18 小时不能提前拿满，最后 6 小时仍按递减速度持续产生积分。
		assertThat(WatchRewardCalculation.progressivePointsTenths(1000, 0, 18 * 3_600, 60))
				.isBetween(8_500, 9_000);
		assertThat(WatchRewardCalculation.progressivePointsTenths(650, 0, 18 * 3_600, 60))
				.isBetween(6_000, 6_500);
		// 已经播满 24 小时后，不再产生新的积分。
		assertThat(WatchRewardCalculation.progressivePointsTenths(1000, 86_400, 3_600, 60)).isZero();
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
