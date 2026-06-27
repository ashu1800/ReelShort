package com.reelshort.backend.payment;

import com.reelshort.backend.order.RechargeOrderStatus;

public record PaymentCallbackResponse(
		String providerEventId,
		String orderNo,
		PaymentEventStatus status,
		RechargeOrderStatus orderStatus,
		String failureReason) {

	public static PaymentCallbackResponse processed(String providerEventId, String orderNo,
			RechargeOrderStatus orderStatus) {
		return new PaymentCallbackResponse(providerEventId, orderNo, PaymentEventStatus.PROCESSED, orderStatus, null);
	}

	public static PaymentCallbackResponse rejected(PaymentEvent event) {
		return new PaymentCallbackResponse(event.providerEventId(), event.orderNo(), event.status(), null,
				event.failureReason());
	}
}
