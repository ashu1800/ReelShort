package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class WithdrawalConversionTests {

	@Test
	void calculatesMinimumPointsAfterFeeAndDirectUsdtValue() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.14"), 10);

		assertThat(conversion.minimumPoints()).isEqualTo(5);
		assertThat(conversion.usdtPer50Points()).isEqualByComparingTo("0.14");
		assertThat(conversion.usdtPerPoint()).isEqualByComparingTo("0.0028");
		assertThat(conversion.usdtAmount(5)).isEqualByComparingTo("0.01");
	}

	@Test
	void roundsUsdtDownToCentsAfterFee() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.14"), 10);

		assertThat(conversion.usdtAmount(4)).isEqualByComparingTo("0.00");
		assertThat(conversion.usdtAmount(101)).isEqualByComparingTo("0.25");
	}

	@Test
	void rejectsAConversionThatCannotProduceMinimumUsdt() {
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> new WithdrawalConversion(new BigDecimal("0.14"), 100).minimumPoints())
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void preservesAllEightRateDecimalsWhenCalculatingAmount() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.00000003"), 10);

		int minimumPoints = conversion.minimumPoints();
		assertThat(minimumPoints).isPositive();
		assertThat(conversion.usdtAmount(minimumPoints)).isEqualByComparingTo("0.01");
	}
}
