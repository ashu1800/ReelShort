package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;

public record TronFeeQuote(
		BigDecimal requiredTrx,
		long totalEnergy,
		long availableEnergy,
		int marginPercent) {
}
