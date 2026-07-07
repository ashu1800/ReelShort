package com.reelshort.backend.points;

public record PointAccountResponse(int balance, int frozenPoints, int availablePoints) {

	public static PointAccountResponse from(PointAccount account) {
		return new PointAccountResponse(account.balance(), account.frozenPoints(), account.availablePoints());
	}
}
