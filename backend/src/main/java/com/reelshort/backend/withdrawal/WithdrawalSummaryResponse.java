package com.reelshort.backend.withdrawal;

public record WithdrawalSummaryResponse(
		int balance,
		int frozenPoints,
		int availablePoints,
		int minimumPoints,
		String usdtPerPoint,
		String usdtPer50Points,
		String minimumUsdt,
		String walletAddress,
		int feePercent) {
}
