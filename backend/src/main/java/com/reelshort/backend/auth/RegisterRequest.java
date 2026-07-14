package com.reelshort.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank String username,
		@NotBlank @Size(min = 6, max = 128) String password,
		@NotBlank String captchaId,
		@NotBlank String captchaAnswer) {
}
