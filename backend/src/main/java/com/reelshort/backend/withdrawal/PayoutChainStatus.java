package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;

public record PayoutChainStatus(PayoutChainState state, int confirmations, String detail,
		BigDecimal actualFeeAmount, String actualFeeAsset) {
	public PayoutChainStatus(PayoutChainState state, int confirmations, String detail) {
		this(state, confirmations, detail, null, null);
	}

	public static PayoutChainStatus of(PayoutChainState state, int confirmations) {
		return new PayoutChainStatus(state, confirmations, null, null, null);
	}

	public static PayoutChainStatus unknown(String detail) {
		return new PayoutChainStatus(PayoutChainState.UNKNOWN, 0, detail, null, null);
	}

	public static PayoutChainStatus confirmed(int confirmations, BigDecimal actualFeeAmount, String actualFeeAsset) {
		return new PayoutChainStatus(PayoutChainState.CONFIRMED, confirmations, null,
				actualFeeAmount, actualFeeAsset);
	}
}
