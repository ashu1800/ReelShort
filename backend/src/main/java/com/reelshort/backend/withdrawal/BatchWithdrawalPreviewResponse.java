package com.reelshort.backend.withdrawal;

import java.util.List;

/**
 * Preview for batch payout. Only configured public hot-wallet addresses are exposed; previewing
 * never accepts signing material and never derives wallet balances from a private key.
 */
public record BatchWithdrawalPreviewResponse(
		String tronHotWalletAddress,
		String ethHotWalletAddress,
		String bepHotWalletAddress,
		String totalUsdt,
		int itemCount,
		List<PreviewItem> items) {

	public record PreviewItem(
			String withdrawalId,
			String userAccount,
			String usdtAmount,
			String network,
			String walletAddress,
			WithdrawalStatus status) {
	}
}
