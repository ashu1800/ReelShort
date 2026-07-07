package com.reelshort.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SmsSendRequest(
		@NotNull SmsVerificationPurpose purpose,
		@NotBlank String countryCode,
		@NotBlank String phoneNumber) {
}
