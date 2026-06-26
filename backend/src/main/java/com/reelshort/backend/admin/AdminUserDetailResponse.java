package com.reelshort.backend.admin;

import java.util.UUID;

import com.reelshort.backend.user.UserStatus;

public record AdminUserDetailResponse(
		UUID id,
		String username,
		UserStatus status,
		int pointBalance,
		long watchRecordCount,
		long pointRecordCount,
		String createdAt) {
}
