package com.reelshort.backend.order;

import java.time.Duration;
import java.time.OffsetDateTime;

import com.reelshort.backend.withdrawal.TronClient.IncomingTransfer;

final class VipPaymentMatcher {

	private VipPaymentMatcher() {
	}

	static boolean matches(VipOrder order, IncomingTransfer transfer, int requiredConfirmations) {
		return matches(order, transfer, requiredConfirmations, Duration.ZERO);
	}

	/**
	 * @param acceptanceGrace 允许的付款延迟：付款时间不超过 {@code expiresAt + acceptanceGrace} 就算按时付款。
	 *                        PENDING 订单先进入宽限期（页面倒计时已归零但订单尚未过期），期间付款仍能自动确认。
	 */
	static boolean matches(VipOrder order, IncomingTransfer transfer, int requiredConfirmations,
			Duration acceptanceGrace) {
		return "PENDING".equals(order.status())
				&& matchesPaymentDetails(order, transfer, requiredConfirmations, acceptanceGrace);
	}

	static boolean matchesLatePayment(VipOrder order, IncomingTransfer transfer, int requiredConfirmations) {
		return matchesLatePayment(order, transfer, requiredConfirmations, Duration.ZERO);
	}

	/**
	 * 过期后补单：用户在有效期内已经付款，但链上确认晚于订单过期。
	 * 除了订单状态（EXPIRED）以外，地址、合约、金额、时间窗和确认数要求与正常确认完全一致。
	 */
	static boolean matchesLatePayment(VipOrder order, IncomingTransfer transfer, int requiredConfirmations,
			Duration acceptanceGrace) {
		return "EXPIRED".equals(order.status())
				&& matchesPaymentDetails(order, transfer, requiredConfirmations, acceptanceGrace);
	}

	private static boolean matchesPaymentDetails(VipOrder order, IncomingTransfer transfer,
			int requiredConfirmations, Duration acceptanceGrace) {
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
		OffsetDateTime deadline = order.expiresAt() == null ? null
				: order.expiresAt().plus(acceptanceGrace == null ? Duration.ZERO : acceptanceGrace);
		return deadline == null || !transfer.blockTimestamp().isAfter(deadline);
	}

	private static boolean equalsExact(String expected, String actual) {
		return expected != null && expected.equals(actual);
	}
}
