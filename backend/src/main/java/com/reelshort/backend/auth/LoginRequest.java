package com.reelshort.backend.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank String countryCode,
		@NotBlank String phoneNumber,
		@NotBlank String password) {
}
