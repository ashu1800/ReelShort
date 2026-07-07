package com.reelshort.backend.withdrawal;

import java.util.UUID;

public record WithdrawalResponse(
		UUID id,
		UUID userId,
		String userAccount,
		int pointAmount,
		String usdtAmount,
		String usdtPerPoint,
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
				request.usdtPerPoint().stripTrailingZeros().toPlainString(), request.network(),
				request.walletAddress(), request.status(), request.txHash(), request.adminNote(),
				request.createdAt(), request.reviewedAt());
	}
}
