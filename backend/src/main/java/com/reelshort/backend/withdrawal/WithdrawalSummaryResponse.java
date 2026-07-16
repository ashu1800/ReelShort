package com.reelshort.backend.withdrawal;

public record WithdrawalSummaryResponse(
		int balance,
		int frozenPoints,
		int availablePoints,
		int minimumPoints,
		String usdtPerPoint,
		String cnyPerPoint,
		String cnyPerUsd,
		String minimumUsd,
		String walletAddress,
		int feePercent) {
}
