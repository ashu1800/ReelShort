package com.reelshort.backend.withdrawal;

import java.util.UUID;

public record WithdrawalResponse(
		UUID id,
		UUID userId,
		String userAccount,
		int pointAmount,
		String usdtAmount,
		String usdtPerPoint,
		String cnyPerPoint,
		String cnyPerUsd,
		String minimumUsd,
		String network,
		String walletAddress,
		WithdrawalStatus status,
		String txHash,
		String adminNote,
		String createdAt,
		String reviewedAt) {

	public static WithdrawalResponse from(WithdrawalRequest request) {
		return from(request, null);
	}

	public static WithdrawalResponse from(WithdrawalRequest request, String userAccount) {
		return new WithdrawalResponse(request.id(), request.userId(), userAccount, request.pointAmount(),
				request.usdtAmount().stripTrailingZeros().toPlainString(),
				request.usdtPerPoint().stripTrailingZeros().toPlainString(), decimal(request.cnyPerPoint()),
				decimal(request.cnyPerUsd()), decimal(request.minimumUsd()), request.network(),
				request.walletAddress(), request.status(), request.txHash(), request.adminNote(),
				request.createdAt(), request.reviewedAt());
	}

	private static String decimal(java.math.BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}
}
