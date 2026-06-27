package com.reelshort.backend.payment;

public record PaymentEventResponse(
		String providerEventId,
		String orderNo,
		String paymentChannel,
		int amountCents,
		PaymentEventStatus status,
		String failureReason,
		String createdAt,
		String processedAt) {

	public static PaymentEventResponse from(PaymentEvent event) {
		return new PaymentEventResponse(event.providerEventId(), event.orderNo(), event.paymentChannel(),
				event.amountCents(), event.status(), event.failureReason(), event.createdAt().toString(),
				event.processedAt().toString());
	}
}
