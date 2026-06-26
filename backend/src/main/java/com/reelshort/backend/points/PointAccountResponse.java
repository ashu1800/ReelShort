package com.reelshort.backend.points;

public record PointAccountResponse(int balance) {

	public static PointAccountResponse from(PointAccount account) {
		return new PointAccountResponse(account.balance());
	}
}
