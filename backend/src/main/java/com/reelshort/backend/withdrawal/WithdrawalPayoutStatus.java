package com.reelshort.backend.withdrawal;

public enum WithdrawalPayoutStatus {
	SIGNING,
	PREPARED,
	BROADCASTED,
	MANUAL_REVIEW,
	CONFIRMED,
	FAILED_RETRYABLE
}
