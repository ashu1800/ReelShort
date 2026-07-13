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
}
