package com.reelshort.backend.withdrawal;

import java.util.List;

/**
 * Result of a best-effort batch payout. Every selected withdrawal receives an item result;
 * prepared transactions are counted separately until a broadcast is observed.
 */
public record BatchWithdrawalResponse(
		int succeeded,
		int failed,
		int pending,
		int stoppedAtIndex,
		String errorMessage,
		List<ItemResult> items) {

	public record ItemResult(
			String withdrawalId,
			String payoutStatus,
			String txHash,
			int confirmationCount,
			String failureReason,
			boolean manualReview,
			String errorMessage) {
	}
}
