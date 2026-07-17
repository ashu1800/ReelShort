package com.reelshort.backend.withdrawal;

public enum WithdrawalStatus {
	PENDING,
	APPROVED,
	REJECTED,
	/** H2: 积分已扣减但链上广播失败，需人工处理（退款或重试广播）。 */
	BROADCAST_FAILED
}
