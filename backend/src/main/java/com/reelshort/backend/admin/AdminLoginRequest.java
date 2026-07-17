package com.reelshort.backend.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminLoginRequest(
		@NotBlank String username,
		@NotBlank String password,
		@Pattern(regexp = "\\d{6}") String totpCode) {
}
