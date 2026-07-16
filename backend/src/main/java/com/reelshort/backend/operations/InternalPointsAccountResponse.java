package com.reelshort.backend.operations;

import java.util.UUID;

import com.reelshort.backend.points.PointAccountResponse;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserStatus;

public record InternalPointsAccountResponse(
		UUID userId,
		String account,
		UserStatus status,
		int balance,
		int frozenPoints,
		int availablePoints,
		boolean vip,
		String vipUntil,
		String walletAddress) {

	static InternalPointsAccountResponse from(UserAccount user, PointAccountResponse account) {
		return new InternalPointsAccountResponse(user.id(), user.username(), user.status(), account.balance(),
				account.frozenPoints(), account.availablePoints(), account.vip(), account.vipUntil(),
				account.walletAddress());
	}
}
