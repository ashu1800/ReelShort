package com.reelshort.backend.withdrawal;

import org.springframework.stereotype.Service;

/**
 * 只读链上核对：打款执行能力默认关闭时仍然可用，供管理员人工对账以及判断一个 BROADCASTED
 * attempt 是否真的上链。本类只查询、不签名、不广播、不修改任何状态。
 */
@Service
public class PayoutAttemptReconciler {

	private final TronClient tronClient;
	private final EthereumClient ethereumClient;
	private final BscClient bscClient;

	public PayoutAttemptReconciler(TronClient tronClient, EthereumClient ethereumClient, BscClient bscClient) {
		this.tronClient = tronClient;
		this.ethereumClient = ethereumClient;
		this.bscClient = bscClient;
	}

	public PayoutChainStatus inspect(WithdrawalPayoutAttempt attempt) {
		String txHash = attempt.txHash();
		if (txHash == null || txHash.isBlank()) {
			return new PayoutChainStatus(PayoutChainState.NOT_FOUND, 0, "attempt has no transaction hash");
		}
		return switch (attempt.network()) {
			case "TRC20" -> tronClient.queryTransactionStatus(txHash);
			case "ERC20" -> ethereumClient.queryTransactionStatus(txHash, attempt.gasPrice());
			case "BEP20" -> bscClient.queryTransactionStatus(txHash, attempt.gasPrice());
			default -> throw new WithdrawalException(400, "unsupported withdrawal network");
		};
	}
}
