package com.reelshort.backend.withdrawal;

public enum WithdrawalStatus {
	PENDING,
	APPROVED,
	REJECTED,
	/** Legacy V13-V19 state retained only so historical rows remain readable. */
	@Deprecated
	BROADCAST_FAILED
}
