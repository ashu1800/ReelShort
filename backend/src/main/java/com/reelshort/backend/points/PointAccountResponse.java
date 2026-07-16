package com.reelshort.backend.points;

public record PointAccountResponse(
		int balance,
		int frozenPoints,
		int availablePoints,
		boolean vip,
		String vipUntil,
		String walletAddress) {

	public static PointAccountResponse from(PointAccount account) {
		return new PointAccountResponse(account.balance(), account.frozenPoints(), account.availablePoints(),
				false, null, null);
	}

	public static PointAccountResponse withVipAndWallet(PointAccount account, boolean vip, String vipUntil,
			String walletAddress) {
		return new PointAccountResponse(account.balance(), account.frozenPoints(), account.availablePoints(),
				vip, vipUntil, walletAddress);
	}
}
