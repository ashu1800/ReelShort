package com.reelshort.backend.points;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PointTransferRequest(
		@NotBlank String recipientAccount,
		@Min(1) int pointAmount) {
}
