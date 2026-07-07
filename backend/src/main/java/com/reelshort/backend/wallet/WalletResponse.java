package com.reelshort.backend.wallet;

public record WalletResponse(String network, String walletAddress, String updatedAt) {

	public static WalletResponse from(UserWallet wallet) {
		if (wallet == null) {
			return new WalletResponse("TRC20", null, null);
		}
		return new WalletResponse(wallet.network(), wallet.walletAddress(), wallet.updatedAt().toString());
	}
}
