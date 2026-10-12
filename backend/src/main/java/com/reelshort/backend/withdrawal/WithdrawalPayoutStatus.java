package com.reelshort.backend.withdrawal;

public enum WithdrawalPayoutStatus {
	SIGNING,
	PREPARED,
	BROADCASTED,
	MANUAL_REVIEW,
	CONFIRMED,
	FAILED_RETRYABLE,

	/** 管理员受审计地作废一个未确认的打款尝试，用于解除卡死的 ACTIVE attempt。 */
	VOIDED
}
