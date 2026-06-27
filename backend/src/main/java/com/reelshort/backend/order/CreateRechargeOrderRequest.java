package com.reelshort.backend.order;

import jakarta.validation.constraints.Positive;

public record CreateRechargeOrderRequest(
		@Positive(message = "amountCents must be positive") int amountCents,
		@Positive(message = "pointAmount must be positive") int pointAmount) {
}
