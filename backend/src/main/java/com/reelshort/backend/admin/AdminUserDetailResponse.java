package com.reelshort.backend.admin;

import java.util.UUID;

import com.reelshort.backend.user.UserStatus;

public record AdminUserDetailResponse(
		UUID id,
		String username,
		UserStatus status,
		boolean vip,
		String vipUntil,
		int pointBalance,
		int frozenPoints,
		int availablePoints,
		String walletNetwork,
		String walletAddress,
		String walletUpdatedAt,
		long watchRecordCount,
		long pointRecordCount,
		long withdrawalRecordCount,
		String createdAt) {
}
