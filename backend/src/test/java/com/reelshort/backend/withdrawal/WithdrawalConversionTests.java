package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class WithdrawalConversionTests {

	@Test
	void minimumPointsIsReachedByTheGrossAmountNotThePayout() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.14"), new BigDecimal("10"), 10);

		// 最低提现 10 USDT 作用在折算总额上，10% 手续费从 USDT 里扣，所以到账 9 USDT。
		assertThat(conversion.minimumPoints()).isEqualTo(3572);
		assertThat(conversion.usdtPer50Points()).isEqualByComparingTo("0.14");
		assertThat(conversion.usdtPerPoint()).isEqualByComparingTo("0.0028");
		assertThat(conversion.minimumUsdt()).isEqualByComparingTo("10");
		assertThat(conversion.usdtGross(3571)).isEqualByComparingTo("9.99");
		assertThat(conversion.usdtGross(3572)).isEqualByComparingTo("10.00");
		assertThat(conversion.usdtAmount(3572)).isEqualByComparingTo("9.00");
	}

	@Test
	void feeComesOffTheUsdtAmountAndPointsAreNotDeductedTwice() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.14"), new BigDecimal("10"), 10);

		// 4000 积分折算 11.20 USDT，扣 10% 后到账 10.08 USDT；扣减积分仍是 4000。
		assertThat(conversion.usdtGross(4000)).isEqualByComparingTo("11.20");
		assertThat(conversion.usdtFee(4000)).isEqualByComparingTo("1.12");
		assertThat(conversion.usdtAmount(4000)).isEqualByComparingTo("10.08");
	}

	@Test
	void feeAndPayoutAlwaysAddUpToTheGrossAmount() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.14"), new BigDecimal("10"), 10);

		for (int points : new int[] { 1, 7, 101, 3572, 4000, 99999 }) {
			assertThat(conversion.usdtFee(points).add(conversion.usdtAmount(points)))
					.isEqualByComparingTo(conversion.usdtGross(points));
		}
	}

	@Test
	void roundsUsdtDownToCents() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.14"), new BigDecimal("10"), 10);

		assertThat(conversion.usdtGross(4)).isEqualByComparingTo("0.01");
		assertThat(conversion.usdtAmount(4)).isEqualByComparingTo("0.00");
		assertThat(conversion.usdtGross(101)).isEqualByComparingTo("0.28");
		assertThat(conversion.usdtAmount(101)).isEqualByComparingTo("0.25");
	}

	@Test
	void aZeroFeePaysTheWholeGrossAmount() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.14"), new BigDecimal("10"), 0);

		assertThat(conversion.usdtAmount(4000)).isEqualByComparingTo(conversion.usdtGross(4000));
		assertThat(conversion.usdtAmount(4000)).isEqualByComparingTo("11.20");
	}

	@Test
	void rejectsAFeeAtOrAboveOneHundredPercent() {
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> new WithdrawalConversion(new BigDecimal("0.14"), new BigDecimal("10"), 100))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAConversionThatCannotProduceMinimumUsdt() {
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> new WithdrawalConversion(new BigDecimal("0.00000001"), new BigDecimal("10"), 10).minimumPoints())
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void preservesAllEightRateDecimalsWhenCalculatingAmount() {
		WithdrawalConversion conversion = new WithdrawalConversion(
				new BigDecimal("0.00000003"), new BigDecimal("0.01"), 10);

		int minimumPoints = conversion.minimumPoints();
		assertThat(minimumPoints).isPositive();
		assertThat(conversion.usdtGross(minimumPoints)).isEqualByComparingTo("0.01");
	}
}
