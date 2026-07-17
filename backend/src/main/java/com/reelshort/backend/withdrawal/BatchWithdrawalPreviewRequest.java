package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Preview request for batch withdrawal payout. Previewing must never receive signing material.
 */
public record BatchWithdrawalPreviewRequest(
		@NotEmpty @Size(max = 100) List<UUID> withdrawalIds) {
}
