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
		String reviewedAt,
		String payoutStatus,
		String payoutTxHash,
		int confirmationCount,
		String failureReason,
		boolean manualReview,
		String actualFeeAmount,
		String actualFeeAsset) {

	public static WithdrawalResponse from(WithdrawalRequest request) {
		return from(request, null);
	}

	public static WithdrawalResponse from(WithdrawalRequest request, String userAccount) {
		return from(request, userAccount, null);
	}

	public static WithdrawalResponse from(WithdrawalRequest request, String userAccount,
			WithdrawalPayoutAttempt attempt) {
		boolean manualConfirmed = request.status() == WithdrawalStatus.APPROVED
				&& (attempt == null || attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW);
		return new WithdrawalResponse(request.id(), request.userId(), userAccount, request.pointAmount(),
				request.usdtAmount().stripTrailingZeros().toPlainString(),
				request.usdtPerPoint().stripTrailingZeros().toPlainString(), decimal(request.cnyPerPoint()),
				decimal(request.cnyPerUsd()), decimal(request.minimumUsd()), request.network(),
				request.walletAddress(), request.status(), request.txHash(), request.adminNote(),
				request.createdAt(), request.reviewedAt(),
				manualConfirmed ? "MANUAL_CONFIRMED" : attempt == null ? null : attempt.status().name(),
				attempt == null ? null : attempt.txHash(),
				attempt == null ? 0 : attempt.confirmationCount(),
				attempt == null ? null : attempt.failureReason(),
				!manualConfirmed && attempt != null && attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW,
				confirmedFeeAmount(attempt), confirmedFeeAsset(attempt));
	}

	private static String confirmedFeeAmount(WithdrawalPayoutAttempt attempt) {
		return attempt != null && attempt.status() == WithdrawalPayoutStatus.CONFIRMED
				? decimal(attempt.actualFeeAmount()) : null;
	}

	private static String confirmedFeeAsset(WithdrawalPayoutAttempt attempt) {
		return attempt != null && attempt.status() == WithdrawalPayoutStatus.CONFIRMED
				? attempt.actualFeeAsset() : null;
	}

	private static String decimal(java.math.BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}
}
