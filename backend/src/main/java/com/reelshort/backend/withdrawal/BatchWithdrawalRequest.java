package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Batch withdrawal payout request. The admin provides hot wallet private keys per-chain
 * (never persisted server-side) plus a TOTP verification code. Each withdrawal is dispatched
 * to the appropriate chain client based on its network.
 */
public record BatchWithdrawalRequest(
		@NotEmpty @Size(max = 100) List<UUID> withdrawalIds,
		@Size(max = 128) String tronPrivateKey,
		@Size(max = 128) String ethPrivateKey,
		@NotBlank @Pattern(regexp = "\\d{6}") String totpCode) {
}
