package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Batch withdrawal payout request. The admin provides the hot wallet private key per-call (never
 * persisted server-side) plus a TOTP verification code.
 */
public record BatchWithdrawalRequest(
		@NotEmpty List<UUID> withdrawalIds,
		@NotBlank @Size(max = 64) String hotWalletPrivateKey,
		@NotBlank @Size(max = 6) String totpCode) {
}
