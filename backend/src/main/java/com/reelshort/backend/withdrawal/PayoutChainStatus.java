package com.reelshort.backend.withdrawal;

public record PayoutChainStatus(PayoutChainState state, int confirmations, String detail) {
	public static PayoutChainStatus of(PayoutChainState state, int confirmations) {
		return new PayoutChainStatus(state, confirmations, null);
	}

	public static PayoutChainStatus unknown(String detail) {
		return new PayoutChainStatus(PayoutChainState.UNKNOWN, 0, detail);
	}
}
