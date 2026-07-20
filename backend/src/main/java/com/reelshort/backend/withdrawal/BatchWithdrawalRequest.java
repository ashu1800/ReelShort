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
		@NotEmpty @Size(max = 10) List<UUID> withdrawalIds,
		@Size(max = 66) @Pattern(regexp = "(?i)^(?:|[0-9a-f]{64}|0x[0-9a-f]{64})$") String tronPrivateKey,
		@Size(max = 66) @Pattern(regexp = "(?i)^(?:|[0-9a-f]{64}|0x[0-9a-f]{64})$") String ethPrivateKey,
		@Size(max = 66) @Pattern(regexp = "(?i)^(?:|[0-9a-f]{64}|0x[0-9a-f]{64})$") String bepPrivateKey,
		@NotBlank @Pattern(regexp = "\\d{6}") String totpCode) {
}
