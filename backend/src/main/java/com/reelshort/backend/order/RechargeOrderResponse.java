package com.reelshort.backend.order;

import java.util.UUID;

public record RechargeOrderResponse(
		UUID id,
		UUID userId,
		String orderNo,
		int amountCents,
		int pointAmount,
		RechargeOrderStatus status,
		String paymentChannel,
		String createdAt,
		String updatedAt) {

	public static RechargeOrderResponse from(RechargeOrder order) {
		return new RechargeOrderResponse(order.id(), order.userId(), order.orderNo(), order.amountCents(),
				order.pointAmount(), order.status(), order.paymentChannel(), order.createdAt().toString(),
				order.updatedAt().toString());
	}
}
