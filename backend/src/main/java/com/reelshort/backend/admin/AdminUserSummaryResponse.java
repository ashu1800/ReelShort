package com.reelshort.backend.admin;

import java.util.UUID;

import com.reelshort.backend.user.UserStatus;

public record AdminUserSummaryResponse(
		UUID id,
		String username,
		UserStatus status,
		int pointBalance,
		String createdAt) {
}
