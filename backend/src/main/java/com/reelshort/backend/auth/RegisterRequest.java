package com.reelshort.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank String countryCode,
		@NotBlank String phoneNumber,
		@NotBlank @Size(min = 6, max = 128) String password,
		@NotBlank String verificationCode) {
}
