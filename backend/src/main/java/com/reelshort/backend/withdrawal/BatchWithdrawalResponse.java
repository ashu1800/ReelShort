package com.reelshort.backend.withdrawal;

import java.util.List;

/**
 * Result of a best-effort batch payout. Every selected withdrawal receives an item result.
 */
public record BatchWithdrawalResponse(
		int succeeded,
		int failed,
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
