package com.reelshort.backend.withdrawal;

import java.util.List;

/**
 * Preview for batch payout: shows the total amount, hot wallet balances, and individual items so
 * the admin can confirm before entering 2FA and private key.
 *
 * @param hotWalletAddress      derived from the private key the admin will provide (shown for
 *                              verification — null if not provided in preview)
 * @param hotWalletUsdtBalance  current USDT balance of the hot wallet
 * @param hotWalletTrxBalance   current TRX balance (for energy/bandwidth fee estimation)
 * @param totalUsdt             sum of all selected withdrawal amounts
 * @param itemCount             number of selected withdrawals
 * @param items                 individual withdrawal details
 */
public record BatchWithdrawalPreviewResponse(
		String hotWalletAddress,
		String hotWalletUsdtBalance,
		String hotWalletTrxBalance,
		String totalUsdt,
		int itemCount,
		List<PreviewItem> items) {

	public record PreviewItem(
			String withdrawalId,
			String userAccount,
			String usdtAmount,
			String walletAddress) {
	}
}
