package com.reelshort.backend.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
		@NotBlank String username,
		@NotBlank String password) {
}
