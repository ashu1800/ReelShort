package com.reelshort.backend.withdrawal;

import java.util.List;

/**
 * Result of a batch payout. Each item reports its outcome; processing stops at the first failure.
 *
 * @param succeeded      count of successfully broadcast transfers
 * @param stoppedAtIndex index of the failed item (-1 if all succeeded)
 * @param errorMessage   error detail if processing stopped
 * @param items          per-withdrawal results
 */
public record BatchWithdrawalResponse(
		int succeeded,
		int stoppedAtIndex,
		String errorMessage,
		List<ItemResult> items) {

	public record ItemResult(
			String withdrawalId,
			String status,   // APPROVED | FAILED | PENDING
			String txHash,
			String errorMessage) {
	}
}
