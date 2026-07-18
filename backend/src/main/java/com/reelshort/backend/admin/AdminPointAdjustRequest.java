package com.reelshort.backend.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPointAdjustRequest(
		int amount,
		@NotBlank @Size(max = 255) String reason,
		@NotBlank @Size(max = 64) String idempotencyKey) {
}
