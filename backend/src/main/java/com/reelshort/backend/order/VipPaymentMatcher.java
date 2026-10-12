package com.reelshort.backend.order;

import com.reelshort.backend.withdrawal.TronClient.IncomingTransfer;

final class VipPaymentMatcher {

	private VipPaymentMatcher() {
	}

	static boolean matches(VipOrder order, IncomingTransfer transfer, int requiredConfirmations) {
		return "PENDING".equals(order.status()) && matchesPaymentDetails(order, transfer, requiredConfirmations);
	}

	/**
	 * 过期后补单：用户在有效期内已经付款，但链上确认晚于订单过期。
	 * 除了订单状态（EXPIRED）以外，地址、合约、金额、时间窗和确认数要求与正常确认完全一致。
	 */
	static boolean matchesLatePayment(VipOrder order, IncomingTransfer transfer, int requiredConfirmations) {
		return "EXPIRED".equals(order.status()) && matchesPaymentDetails(order, transfer, requiredConfirmations);
	}

	private static boolean matchesPaymentDetails(VipOrder order, IncomingTransfer transfer,
			int requiredConfirmations) {
		if (transfer == null || transfer.txHash() == null
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
