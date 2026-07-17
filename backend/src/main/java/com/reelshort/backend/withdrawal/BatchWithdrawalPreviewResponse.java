package com.reelshort.backend.withdrawal;

import java.util.List;

/**
 * Preview for batch payout: shows the total amount, hot wallet balances for both chains, and
 * individual items so the admin can confirm before entering 2FA and private keys.
 *
 * @param tronHotWalletAddress   derived from the Tron private key (null if not provided)
 * @param tronUsdtBalance        current TRC20 USDT balance of the Tron hot wallet
 * @param tronTrxBalance         current TRX balance (for energy/bandwidth fee estimation)
 * @param ethHotWalletAddress    derived from the Ethereum private key (null if not provided)
 * @param ethUsdtBalance         current ERC-20 USDT balance of the Ethereum hot wallet
 * @param ethEthBalance          current ETH balance (for gas fee estimation)
 * @param totalUsdt              sum of all selected withdrawal amounts
 * @param itemCount              number of selected withdrawals
 * @param items                  individual withdrawal details
 */
public record BatchWithdrawalPreviewResponse(
		String tronHotWalletAddress,
		String tronUsdtBalance,
		String tronTrxBalance,
		String ethHotWalletAddress,
		String ethUsdtBalance,
		String ethEthBalance,
		String totalUsdt,
		int itemCount,
		List<PreviewItem> items) {

	public record PreviewItem(
			String withdrawalId,
			String userAccount,
			String usdtAmount,
			String network,
			String walletAddress) {
	}
}
