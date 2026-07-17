package com.reelshort.backend.withdrawal;

public record PayoutBroadcastResult(PayoutBroadcastDisposition disposition, String detail) {
	public static PayoutBroadcastResult accepted() {
		return new PayoutBroadcastResult(PayoutBroadcastDisposition.ACCEPTED, null);
	}

	public static PayoutBroadcastResult rejected(String detail) {
		return new PayoutBroadcastResult(PayoutBroadcastDisposition.EXPLICITLY_REJECTED, detail);
	}

	public static PayoutBroadcastResult unknown(String detail) {
		return new PayoutBroadcastResult(PayoutBroadcastDisposition.UNKNOWN, detail);
	}
}
