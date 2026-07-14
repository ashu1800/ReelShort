package com.reelshort.backend.wallet;

import jakarta.validation.constraints.NotBlank;

public record BankCardBindRequest(
		@NotBlank String holderName,
		@NotBlank String cardNumber,
		@NotBlank String expiryMonth,
		@NotBlank String expiryYear,
		@NotBlank String cvv) {
}
