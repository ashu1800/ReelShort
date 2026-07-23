package com.reelshort.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
		@NotBlank String username,
		@NotBlank String password,
		@NotNull LoginSource loginSource) {
}
