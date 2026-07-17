package com.reelshort.backend.withdrawal;

public enum WithdrawalPayoutStatus {
	PREPARED,
	BROADCASTED,
	MANUAL_REVIEW,
	CONFIRMED,
	FAILED_RETRYABLE
}
