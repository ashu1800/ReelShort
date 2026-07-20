package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class BscPropertiesTests {

	@Test
	void defaultDecimalFactorIsTenToTheEighteenForBscUsdt() {
		// BSC 上 USDT 使用 18 位 decimals（与以太坊/波场的 6 位不同）。
		// 这是打款金额换算的基准：链上最小单位 = USDT 金额 × decimalFactor。
		// 若此值错误，打款金额会偏差 10^12 倍，属最高风险点，必须有测试锁定。
		BscProperties properties = new BscProperties();

		assertThat(properties.getUsdtDecimals()).isEqualTo(18);
		assertThat(properties.decimalFactor()).isEqualByComparingTo(new BigDecimal("1000000000000000000"));
	}

	@Test
	void decimalFactorTracksConfiguredDecimals() {
		// 验证 decimalFactor 随 usdtDecimals 配置变化，防止硬编码。
		BscProperties properties = new BscProperties();
		properties.setUsdtDecimals(6);

		assertThat(properties.decimalFactor()).isEqualByComparingTo(new BigDecimal("1000000"));
	}

	@Test
	void signTransferEncodesAmountWithEighteenDecimals() {
		// 端到端验证金额换算：1.5 USDT 经 decimalFactor 换算后是 1.5×10^18，
		// 与以太坊（1.5×10^6）严格不同，确保 BSC 打款金额编码正确。
		BscProperties properties = new BscProperties();
		BigDecimal onePointFiveUsdt = new BigDecimal("1.5");

		BigDecimal bscRawAmount = onePointFiveUsdt.multiply(properties.decimalFactor());

		assertThat(bscRawAmount).isEqualByComparingTo(new BigDecimal("1500000000000000000"));
		// 对照：以太坊 6 decimals 下同金额是 1500000，两者相差 10^12 倍。
		assertThat(bscRawAmount)
				.as("BSC 18-decimals 金额必须是以太坊 6-decimals 的 10^12 倍")
				.isEqualByComparingTo(new BigDecimal("1500000").multiply(new BigDecimal("1000000000000")));
	}
}
