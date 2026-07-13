package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class WithdrawalConversionTests {

	@Test
	void calculatesDefaultMinimumPointsAndUsdtValue() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.02"), new BigDecimal("7.2"),
				new BigDecimal("10"));

		assertThat(conversion.minimumPoints()).isEqualTo(3600);
		assertThat(conversion.usdtPerPoint()).isEqualByComparingTo("0.00277778");
		assertThat(conversion.usdtAmount(3600)).isEqualByComparingTo("10.000000");
	}

	@Test
	void roundsStoredUsdtAmountToSixDecimalPlaces() {
		WithdrawalConversion conversion = new WithdrawalConversion(new BigDecimal("0.02"), new BigDecimal("7.2"),
				new BigDecimal("10"));

		assertThat(conversion.usdtAmount(1)).isEqualByComparingTo("0.002778");
	}
}
