package com.reelshort.backend.order;

import com.reelshort.backend.withdrawal.TronClient.IncomingTransfer;

final class VipPaymentMatcher {

	private VipPaymentMatcher() {
	}

	static boolean matches(VipOrder order, IncomingTransfer transfer, int requiredConfirmations) {
		if (!"PENDING".equals(order.status()) || transfer == null || transfer.txHash() == null
				|| transfer.txHash().isBlank() || !transfer.successful()
				|| transfer.confirmationCount() < requiredConfirmations) {
			return false;
		}
		if (!"TRC20".equals(order.receivingNetwork())
				|| !equalsExact(order.receivingWalletAddress(), transfer.recipient())
				|| !equalsExact(order.tokenContractAddress(), transfer.contract())
				|| order.payableAmount().compareTo(transfer.amount()) != 0
				|| transfer.blockTimestamp() == null
				|| transfer.blockTimestamp().isBefore(order.createdAt())) {
			return false;
		}
		return order.expiresAt() == null || !transfer.blockTimestamp().isAfter(order.expiresAt());
	}

	private static boolean equalsExact(String expected, String actual) {
		return expected != null && expected.equals(actual);
	}
}
