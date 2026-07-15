package com.reelshort.backend.wallet;

public record WalletResponse(String network, String walletAddress, String updatedAt,
		String vipUntil, String vipPriceUsdt, String vipCollectionAddress) {

	public static WalletResponse from(UserWallet wallet) {
		return new WalletResponse(
				wallet == null ? "TRC20" : wallet.network(),
				wallet == null ? null : wallet.walletAddress(),
				wallet == null ? null : wallet.updatedAt().toString(),
				null, null, null);
	}

	public static WalletResponse withVip(UserWallet wallet, String vipUntil, String vipPriceUsdt,
			String vipCollectionAddress) {
		return new WalletResponse(
				wallet == null ? "TRC20" : wallet.network(),
				wallet == null ? null : wallet.walletAddress(),
				wallet == null ? null : wallet.updatedAt().toString(),
				vipUntil, vipPriceUsdt, vipCollectionAddress);
	}
}
