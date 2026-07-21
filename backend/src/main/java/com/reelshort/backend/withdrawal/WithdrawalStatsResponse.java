package com.reelshort.backend.withdrawal;

public record WithdrawalStatsResponse(
		WithdrawalStatsRange range,
		String from,
		String to,
		String totalUsdt,
		long payoutCount) {
}
