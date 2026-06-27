package com.reelshort.backend.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PaymentCallbackRequest(
		@NotBlank String providerEventId,
		@NotBlank String orderNo,
		@NotBlank String paymentChannel,
		@Positive int amountCents) {
}
