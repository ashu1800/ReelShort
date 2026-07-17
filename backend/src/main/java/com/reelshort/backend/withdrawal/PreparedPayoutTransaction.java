package com.reelshort.backend.withdrawal;

import java.math.BigInteger;

public record PreparedPayoutTransaction(
		String network,
		String hotWalletAddress,
		String tokenContractAddress,
		long chainId,
		BigInteger nonce,
		String signedRawTransaction,
		String txHash) {
}
