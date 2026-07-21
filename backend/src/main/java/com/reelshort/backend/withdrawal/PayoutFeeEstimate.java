package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;

public record PayoutFeeEstimate(
		String network,
		String asset,
		int transactionCount,
		BigDecimal estimatedAmount,
		String estimateType) {
}
